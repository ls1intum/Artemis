/**
 * Sets up the MonacoEnvironment for the monaco editor's service worker.
 */
export function MonacoConfig() {
    self.MonacoEnvironment = {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        getWorkerUrl: function (workerId: string, label: string) {
            /*
             * This is the AMD-based service worker, which comes bundled with a few special workers for selected languages.
             * (e.g.: javascript, typescript, html, css)
             *
             * It is also possible to use an ESM-based approach, which requires a little more setup and case distinctions in this method.
             * At the moment, it seems that the ESM-based approaches are incompatible with the Artemis client, as they would require custom builders.
             * Support for custom builders was removed in #6546.
             */
            return 'vs/base/worker/workerMain.js';
        },
    };
}
