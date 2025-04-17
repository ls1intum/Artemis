import fs from 'fs';
import path from 'path';

const source = path.resolve('node_modules/pdfjs-dist/build/pdf.worker.min.mjs');
const destinationDir = path.resolve('src/main/webapp/content/scripts');
const destination = path.join(destinationDir, 'pdf.worker.min.mjs');

fs.mkdirSync(destinationDir, { recursive: true });

fs.copyFileSync(source, destination);
