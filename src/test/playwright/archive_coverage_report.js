const fs = require('fs');
const path = require('path');
const archiver = require('archiver');

const outputDir = path.join(__dirname, 'test-reports/monocart-report/coverage/coverage-e2e-tests.zip');
const output = fs.createWriteStream(outputDir);
const archive = archiver('zip', { zlib: { level: 9 } });

output.on('close', function () {
    console.log('Zip file of coverage report created:', archive.pointer() + ' total bytes');
});

archive.on('error', function (err) {
    throw err;
});

archive.pipe(output);
const archiveDir = path.join(__dirname, 'test-reports/monocart-report/coverage/lcov-report');
console.log('Archiving coverage report:', archiveDir);
console.log('Output file:', outputDir);
archive.directory(archiveDir, false);
archive.finalize();
