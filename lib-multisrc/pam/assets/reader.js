// Where the chapter's reader is on screen and what covers it; nothing inside the reader is read, only its box.
() => {
    const empty = { scale: devicePixelRatio, height: innerHeight, clear: [], reader: null };
    // The site's own check before reading
    if (document.getElementById('captcha-container')) return { ...empty, gate: 'check' };
    const main = document.querySelector('main');
    if (!main) return null;
    // A chapter kept for accounts: the chapter data the site renders says so, and the page offers a subscription
    const chapter = JSON.parse(document.getElementById('app')?.dataset.page ?? 'null')?.props?.data;
    if (chapter?.type && chapter.type !== 'public' && main.querySelector('a[href$="/subscription"]')) return { ...empty, gate: 'locked' };
    // The reader: the tallest page-wide box that shows no text but its own buttons' (a page's reload button) and isn't
    // mostly another site's frame (an ad)
    const letters = (el) => (el.innerText ?? '').replace(/\s/g, '').length;
    const wordy = (el) => letters(el) > [...el.querySelectorAll('button')].reduce((sum, button) => sum + letters(button), 0);
    const foreign = (el) => {
        const height = el.getBoundingClientRect().height;
        return [el, ...el.querySelectorAll('iframe')].some(
            (frame) =>
                frame.tagName === 'IFRAME' &&
                new URL(frame.src || location.href, location.href).host !== location.host &&
                frame.getBoundingClientRect().height * 2 > height,
        );
    };
    let section = null;
    let tallest = 0;
    for (const el of main.querySelectorAll('*')) {
        const rect = el.getBoundingClientRect();
        if (rect.height <= tallest || rect.width < main.clientWidth / 2) continue;
        if (wordy(el) || foreign(el)) continue;
        section = el;
        tallest = rect.height;
    }
    if (!section) return empty;
    // The same reader from one read to the next, numbered in this world only
    const seen = (globalThis.readers ??= { ids: new WeakMap(), next: 1 });
    if (!seen.ids.has(section)) seen.ids.set(section, seen.next++);
    // Screen rows where nothing (an ad, a toolbar) covers the reader: the boxes of whatever sits on top of it
    const box = section.getBoundingClientRect();
    const xs = [0.02, 0.2, 0.35, 0.5, 0.65, 0.8, 0.98].map((f) => box.left + box.width * f);
    const covered = [];
    for (let y = Math.max(0, box.top); y < Math.min(innerHeight, box.bottom); y += 8) {
        for (const x of xs) {
            const cover = document.elementFromPoint(x, y);
            if (!cover || section.contains(cover)) continue;
            // The covering element's own box, and at least the sampled row
            const rect = cover.getBoundingClientRect();
            covered.push([Math.min(rect.top, y - 8) - 1, Math.max(rect.bottom, y + 8) + 1]);
        }
    }
    // Fixed or sticky layers above the page too: ads and toolbars that a tap would pass through
    const visible = (el) => {
        for (let node = el; node && node !== document.body; node = node.parentElement) {
            const style = getComputedStyle(node);
            if (style.visibility === 'hidden' || style.display === 'none' || Number(style.opacity) < 0.05) return false;
        }
        return true;
    };
    let dismiss = null;
    for (const el of document.body.getElementsByTagName('*')) {
        const style = getComputedStyle(el);
        if (style.position !== 'fixed' && style.position !== 'sticky') continue;
        if (!(Number(style.zIndex) > 0) || el.contains(section) || section.contains(el)) continue;
        const rect = el.getBoundingClientRect();
        if (rect.width < 1 || rect.height < 1 || rect.right <= box.left || rect.left >= box.right) continue;
        if (rect.bottom <= 0 || rect.top >= innerHeight || !visible(el)) continue;
        // A layer that only frames its children (a toolbar's full-width wrapper) covers where they are
        const parts = style.pointerEvents === 'none' ? [...el.children].map((child) => child.getBoundingClientRect()) : [rect];
        for (const part of parts) if (part.height >= 1 && part.width >= 1) covered.push([part.top - 1, part.bottom + 1]);
        // An ad: a floating box around an iframe, closed by its own button
        const close = el.querySelector('iframe') && el.querySelector('button');
        if (close && !dismiss) {
            const at = close.getBoundingClientRect();
            const x = at.left + at.width / 2;
            const y = at.top + at.height / 2;
            if (close.contains(document.elementFromPoint(x, y))) dismiss = { x, y, clear: true };
        }
    }
    const clear = [];
    let from = 0;
    for (const [top, bottom] of covered.sort((a, b) => a[0] - b[0])) {
        if (top > from) clear.push({ top: from, bottom: Math.min(top, innerHeight) });
        from = Math.max(from, bottom);
    }
    if (from < innerHeight) clear.push({ top: from, bottom: innerHeight });
    // A page's own reload button, after the reader gave up on it
    const reload = [...section.querySelectorAll('button')]
        .map((button) => {
            const at = button.getBoundingClientRect();
            const x = at.left + at.width / 2;
            const y = at.top + at.height / 2;
            return y > 0 && y < innerHeight && button.contains(document.elementFromPoint(x, y)) ? { x, y, clear: true } : null;
        })
        .find(Boolean);
    // Other sites' frames (ads) within the reader: their rows are no part of the chapter
    const ads = [...section.querySelectorAll('iframe')]
        .filter((frame) => new URL(frame.src || location.href, location.href).host !== location.host)
        .map((frame) => frame.getBoundingClientRect())
        .filter((rect) => rect.height >= 1)
        .map((rect) => ({ top: rect.top, bottom: rect.bottom }));
    return {
        ...empty,
        clear,
        dismiss,
        reload,
        ads,
        reader: { id: seen.ids.get(section), top: box.top, left: box.left, width: box.width, height: box.height },
    };
}
