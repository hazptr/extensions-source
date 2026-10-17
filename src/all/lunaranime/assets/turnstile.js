((token) => {
    // Turnstile API for the reader, answering with a token solved by the app
    let callback;
    window.turnstile = {
        ready: (onReady) => onReady(),
        render: (container, options) => {
            callback = options.callback;
            return "lunar";
        },
        execute: () => setTimeout(() => callback(token)),
        reset: () => {},
    };
})
