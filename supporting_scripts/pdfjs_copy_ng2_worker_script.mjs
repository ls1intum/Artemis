/**
 * ng2-pdf-viewer bundles its own pdfjs-dist version and requires a matching worker script.
 * This script copies the worker to a publicly served location so ng2-pdf-viewer can load it.
 */

import fs from 'fs';
import path from 'path';

const source = path.resolve('node_modules/ng2-pdf-viewer/node_modules/pdfjs-dist/legacy/build/pdf.worker.min.mjs');
const destinationDir = path.resolve('src/main/webapp/content/scripts');
const destination = path.join(destinationDir, 'pdf.worker.ng2.min.mjs');

fs.mkdirSync(destinationDir, { recursive: true });

if (!fs.existsSync(source)) {
    console.warn(`[pdfjs_copy_ng2_worker_script] Missing worker source: ${source}`);
} else {
    fs.copyFileSync(source, destination);
}
