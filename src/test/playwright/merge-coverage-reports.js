// merge-coverage.js
import fs from 'fs';
import CoverageReport from 'monocart-coverage-reports';

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
