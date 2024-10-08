/**
 * Sets up the MonacoEnvironment for the monaco editor's service worker.
 * See https://github.com/microsoft/monaco-editor/blob/main/samples/browser-esm-esbuild/index.js
 */
export function MonacoConfig() {
    self.MonacoEnvironment = {
        getWorkerUrl: (_moduleId: string, label: string): string => {
            if (label === 'json') {
                return './vs/language/json/json.worker.js';
            }
            if (label === 'css' || label === 'scss' || label === 'less') {
                return './vs/language/css/css.worker.js';
            }
            if (label === 'html' || label === 'handlebars' || label === 'razor') {
                return './vs/language/html/html.worker.js';
            }
            if (label === 'typescript' || label === 'javascript') {
                return './vs/language/typescript/ts.worker.js';
            }
            return './vs/editor/editor.worker.js';
        },
    };
}
