// merge-coverage.js
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import CoverageReport from 'monocart-coverage-reports';
import archiver from 'archiver';

const inputDir = ['./test-reports/monocart-report-parallel/coverage/raw', './test-reports/monocart-report-sequential/coverage/raw'];
const coverageOptions = {
    name: 'E2E Coverage Report',
    inputDir,
    outputDir: './test-reports/monocart-report',

    filter: {
        '**/src/**': true,
        '**/node_modules/**': false,
        '**/**': true,
    },

    sourcePath: (filePath) => {
        return filePath;
    },

    reports: ['json', 'lcov'],

    onEnd: () => {
        inputDir.forEach((p) => {
            fs.rmSync(p, {
                recursive: true,
                force: true,
            });
        });
    },
};
await new CoverageReport(coverageOptions).generate();

// Archive the lcov coverage report
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const zipFilePath = path.join(__dirname, 'test-reports/monocart-report/e2e-client-coverage.zip');
const output = fs.createWriteStream(zipFilePath);
const archive = archiver('zip', {
    zlib: { level: 9 },
});

output.on('close', () => {
    console.log(`E2E Client Coverage report archive created: ${zipFilePath}`);
});

archive.on('error', (err) => {
    throw err;
});

archive.pipe(output);
archive.directory(path.join(__dirname, 'test-reports/monocart-report/lcov-report/'), false);
await archive.finalize();
