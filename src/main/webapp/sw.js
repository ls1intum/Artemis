self.addEventListener('fetch', (event) => {
    if (
        event &&
        event.request &&
        event.request.url &&
        event.request.url.includes('/api') // 🖍️
    ) {
        event.stopImmediatePropagation();
    }
});

self.importScripts('./ngsw-worker.js');
