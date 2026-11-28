(imagePath) => {
    const lists = [];
    new MutationObserver((records) => {
        for (const record of records) {
            for (const node of record.addedNodes) {
                if (node.nodeType !== Node.ELEMENT_NODE) continue;
                if (node.matches('script[type="text/plain"]')) lists.push(node);
                lists.push(...node.querySelectorAll('script[type="text/plain"]'));
            }
        }
    }).observe(document, { childList: true, subtree: true });

    let completeAt = 0;
    let solvedAt = 0;
    return () => {
        if (document.readyState !== 'complete') return null;
        const snapshot = (pageUrl) => ({
            url: location.href,
            html: document.documentElement.outerHTML,
            session: { ...sessionStorage },
            lists: lists.map((node) => node.textContent),
            pageUrl,
        });
        if (document.querySelector('iframe[src*="player"]')) return snapshot(null);
        const image = performance
            .getEntriesByType('resource')
            .find((entry) => entry.name.includes(imagePath) && entry.responseStatus === 200);
        if (image) return snapshot(image.name);
        if (document.querySelector('.reading-content button[onclick*="reload"]')) return { ...snapshot(null), reload: true };
        const tokens = [...document.querySelectorAll('[name="cf-turnstile-response"]')];
        if (tokens.some((input) => !input.value)) {
            completeAt = 0;
            return null;
        }
        if (tokens.length) {
            solvedAt ||= Date.now();
            if (Date.now() - solvedAt > 3000) return { ...snapshot(null), reload: true };
        }
        completeAt ||= Date.now();
        return Date.now() - completeAt > 20000 ? snapshot(null) : null;
    };
}
