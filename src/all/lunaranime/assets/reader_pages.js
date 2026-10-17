(post) => {
    // The reader parses decrypted chapter data with JSON.parse from a blank iframe
    const contentWindow = Object.getOwnPropertyDescriptor(HTMLIFrameElement.prototype, "contentWindow");
    const wrapped = new WeakSet();

    Object.defineProperty(HTMLIFrameElement.prototype, "contentWindow", {
        ...contentWindow,
        get() {
            const frame = contentWindow.get.call(this);
            if (!frame || wrapped.has(frame)) return frame;
            wrapped.add(frame);
            try {
                const parse = frame.JSON.parse;
                frame.JSON.parse = function (...args) {
                    const result = parse.apply(this, args);
                    if (Array.isArray(result?.data?.images) || result?.data?.is_coming_soon) post(JSON.stringify(result));
                    return result;
                };
            } catch (e) {
                // Cross-origin frame
            }
            return frame;
        },
    });

    // Locked chapters answer the page list request with a 403 and plain flags
    const fetch = window.fetch;
    const lockFlags = ["access_denied", "is_locked", "is_password_protected", "is_kofi_locked", "is_points_locked", "is_mobile_only"];
    window.fetch = async function (...args) {
        const response = await fetch.apply(this, args);
        const url = String(args[0]?.url ?? args[0]);
        if (response.status === 403 && url.includes("/api/manga/r/")) {
            response.clone().json()
                .then((body) => lockFlags.some((flag) => body?.[flag]) && post(JSON.stringify({ locked: true })))
                .catch(() => {});
        }
        return response;
    };
}
