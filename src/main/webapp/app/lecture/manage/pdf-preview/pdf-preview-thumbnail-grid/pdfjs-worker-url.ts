/**
 * PDF.js requires a static asset URL to load its worker, which is used in pdf-preview-thumbnail-grid.component.ts.
 *
 * Vite provides this URL via the `?url` import syntax, allowing us to reference the worker file
 * without manually copying it into the public assets. This also ensures that the worker script
 * stays up to date with future PDF.js updates.
 *
 * However, Jest does not support `?url` imports, and will throw an error if it encounters this
 * syntax in a test environment.
 *
 * By isolating the import here, we can safely use the worker URL in the component,
 * while keeping it out of Jest's module resolution path and avoiding test failures.
 */

// @ts-expect-error – `?url` is not recognized by TypeScript
import pdfjsWorker from 'pdfjs-dist/build/pdf.worker.min.mjs?worker&url';
export default pdfjsWorker;
