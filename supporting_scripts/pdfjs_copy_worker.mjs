/**
 * pdfjs-dist (used in pdf-preview-thumbnail-grid.component.ts) requires a URL pointing to a worker script to process PDFs.
 * This script copies the worker script to a location where it can be referenced by pdfjs-dist.
 * This script is called in the postinstall lifecycle hook in package.json.
 */

import fs from 'fs';
import path from 'path';

const source = path.resolve('node_modules/pdfjs-dist/build/pdf.worker.min.mjs');
const destinationDir = path.resolve('src/main/webapp/content/scripts');
const destination = path.join(destinationDir, 'pdf.worker.min.mjs');

fs.mkdirSync(destinationDir, { recursive: true });

fs.copyFileSync(source, destination);
