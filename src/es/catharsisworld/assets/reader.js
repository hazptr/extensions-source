function (post, controlName) {
    if (window.top !== window) return;

    const originalDrawImage = CanvasRenderingContext2D.prototype.drawImage;
    const originalXhrOpen = XMLHttpRequest.prototype.open;
    const originalFetch = window.fetch;

    const startedAt = Date.now();
    const draws = new Map();
    const pages = new Map();
    const sending = new Map();
    const retried = new Map();
    const solved = new WeakSet();
    const xhrUrls = new WeakMap();
    let solves = 0;
    let solvedAt = 0;
    let pending = 0;
    let lastSettled = Date.now();
    let pageList = null;
    let lastTurn = 0;
    let lastRefusal = null;

    const send = message => post(JSON.stringify(message));
    const sameOrigin = url => {
        try {
            return new URL(String(url), location.href).origin === location.origin;
        } catch (e) {
            return false;
        }
    };

    // Pages are the items shaped like the one holding the first page drawn: same tag and attribute names, under the
    // element holding all the reader's canvases. Items stay while a page loads, fails or is hidden; canvases don't
    const signature = element => element.tagName + "[" + element.getAttributeNames().sort().join(",") + "]";
    const tagPath = element => {
        const path = [];
        for (let node = element; node && node !== document.body; node = node.parentElement) path.push(node.tagName);
        return path.join("<");
    };
    const findPageList = canvas => {
        const canvases = [...document.getElementsByTagName("canvas")].filter(other => tagPath(other) === tagPath(canvas));
        if (canvases.length < 2) return { single: canvas };
        let container = canvas.parentElement;
        while (!canvases.every(other => container.contains(other))) container = container.parentElement;
        let item = canvas;
        while (item.parentElement !== container) item = item.parentElement;
        return { container, signature: signature(item) };
    };
    const pageItems = () => {
        if (pageList === null) return [];
        if (pageList.single) return [pageList.single];
        return [...pageList.container.children].filter(child => signature(child) === pageList.signature);
    };

    // The reader draws each page from its decoded bitmap, in slices when scrambled, then marks it up with text and fills
    CanvasRenderingContext2D.prototype.drawImage = function (source) {
        const canvas = this.canvas;
        if ((source instanceof ImageBitmap || source instanceof HTMLImageElement) && canvas instanceof HTMLCanvasElement && canvas.isConnected) {
            let record = draws.get(canvas);
            if (!record || record.source !== source || record.closed) {
                const current = { source, calls: [], width: canvas.width, height: canvas.height, closed: false };
                draws.set(canvas, current);
                setTimeout(() => {
                    current.closed = true;
                    pageDrawn(canvas, current);
                }, 0);
                record = current;
            }
            record.calls.push([...arguments].slice(1));
        }
        return originalDrawImage.apply(this, arguments);
    };

    const pageDrawn = (canvas, record) => {
        if (pageList === null) {
            pageList = findPageList(canvas);
            send({ type: "total", total: pageItems().length });
        }
        const index = pageItems().findIndex(item => item.contains(canvas));
        if (index < 0) return;
        pages.set(index, record);
        send({ type: "ready", index });
    };

    // The page as drawn, without the reader's marks
    const encode = async record => {
        const canvas = new OffscreenCanvas(record.width, record.height);
        const context = canvas.getContext("2d");
        context.imageSmoothingEnabled = false;
        for (const call of record.calls) context.drawImage(record.source, ...call);
        const blob = await canvas.convertToBlob({ type: "image/jpeg", quality: 0.92 });
        const data = await new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(String(reader.result));
            reader.onerror = () => reject(reader.error);
            reader.readAsDataURL(blob);
        });
        return data.substring(data.indexOf(",") + 1);
    };

    // The site's own requests: a 429 slows every reader down, a refusal explains a reader that shows no pages
    const watch = (url, status, text) => {
        if (!sameOrigin(url)) return;
        if (status === 429) send({ type: "slow" });
        if (status === 401 || status === 403) {
            let code = null;
            try {
                code = JSON.parse(text).code || null;
            } catch (e) {}
            if (code || !lastRefusal || !lastRefusal.code) lastRefusal = { status, code };
        }
    };
    const settled = () => {
        pending--;
        lastSettled = Date.now();
    };
    XMLHttpRequest.prototype.open = function (method, url) {
        xhrUrls.set(this, url);
        if (sameOrigin(url)) {
            this.addEventListener("loadstart", () => pending++);
            this.addEventListener("loadend", settled);
        }
        this.addEventListener("load", () => {
            const text = this.responseType === "" || this.responseType === "text" ? this.responseText : JSON.stringify(this.response);
            watch(xhrUrls.get(this), this.status, text);
        });
        return originalXhrOpen.apply(this, arguments);
    };
    window.fetch = function (input) {
        const result = originalFetch.apply(this, arguments);
        const url = typeof input === "string" ? input : (input && input.url) || "";
        if (!sameOrigin(url)) return result;
        pending++;
        result.then(response => {
            if (response.status === 401 || response.status === 403) {
                response.clone().text().then(text => watch(url, response.status, text), () => {});
            } else {
                watch(url, response.status, "");
            }
        }, () => {}).finally(settled);
        return result;
    };
    // When the site last finished answering the page, bodies included
    const lastActivity = () => Math.max(
        lastSettled,
        ...performance.getEntriesByType("resource")
            .filter(entry => (entry.initiatorType === "fetch" || entry.initiatorType === "xmlhttprequest") && sameOrigin(entry.name))
            .map(entry => performance.timeOrigin + entry.responseEnd),
    );

    // The anti-bot widget is a custom element with a solve() method; each one shown is solved once
    const solveWidget = widget => {
        if (!widget || solved.has(widget)) return true;
        // Solved three times and shown again: the site keeps refusing it, say why if it did
        if (solves >= 3) {
            send(lastRefusal ? { type: "error", ...lastRefusal } : { type: "error", status: 0, code: "CAP_FAILED" });
            return false;
        }
        solved.add(widget);
        solves++;
        solvedAt = Date.now();
        // The widget resets itself when its token expires
        widget.addEventListener("reset", () => solved.delete(widget), { once: true });
        widget.solve();
        return true;
    };

    // No page, no widget left to solve and nothing loading: the reader shows something else, like a login card
    const showsNoPages = widget => (!widget || solved.has(widget)) &&
        pageList === null &&
        pending <= 0 &&
        Date.now() - startedAt > 15000 &&
        Date.now() - solvedAt > 30000 &&
        Date.now() - lastActivity() > 8000;

    const sendWanted = want => {
        const now = Date.now();
        for (const index of want) {
            const record = pages.get(index);
            if (!record || now - (sending.get(index) || 0) < 10000) continue;
            sending.set(index, now);
            encode(record).then(
                data => {
                    send({ type: "page", index, data });
                    // Asked for again later, it goes again soon
                    sending.set(index, Date.now() - 8000);
                },
                () => sending.delete(index),
            );
        }
    };

    // A page the reader gave up on shows a button and no page in its item: its retry
    const retryFailed = want => {
        const items = pageItems();
        if (items.length < 2) return;
        const now = Date.now();
        for (const index of want) {
            const item = items[index];
            if (pages.has(index) || !item || [...item.getElementsByTagName("canvas")].some(canvas => canvas.getClientRects().length)) continue;
            const button = item.querySelector("button");
            if (!button || now - (retried.get(index) || 0) < 15000) continue;
            retried.set(index, now);
            button.click();
        }
    };

    // One scroll to the next wanted page for each turn Kotlin hands out
    const step = (want, turn) => {
        if (turn === lastTurn || pageList === null) return;
        lastTurn = turn;
        const next = want.find(index => !pages.has(index));
        const target = next === undefined ? null : pageItems()[next];
        if (target) target.scrollIntoView({ block: "start", behavior: "instant" });
    };

    setInterval(() => {
        const widget = [...document.querySelectorAll("*")].find(element => element.tagName.includes("-") && typeof element.solve === "function");
        if (showsNoPages(widget)) {
            send({ type: "error", status: lastRefusal ? lastRefusal.status : 0, code: lastRefusal ? lastRefusal.code : "NO_PAGES" });
            return;
        }
        if (!solveWidget(widget)) return;

        const { want, turn } = window[controlName] || { want: [], turn: 0 };
        sendWanted(want);
        // The reader draws pages again when it gets focus after losing it
        if (want.some(index => !pages.has(index)) && !document.hasFocus()) window.dispatchEvent(new Event("focus"));
        retryFailed(want);
        step(want, turn);
    }, 400);
}
