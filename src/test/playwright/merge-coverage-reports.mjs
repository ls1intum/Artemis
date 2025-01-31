// merge-coverage-reports.mjs
import path from 'path';
import { fileURLToPath } from 'url';
import archiver from 'archiver';
import coverage from 'istanbul-lib-coverage';
import reports from 'istanbul-reports';
import libReport from 'istanbul-lib-report';
import fs from 'fs';

const coverageJsonParallel = './test-reports/monocart-report-parallel/coverage/coverage-final.json';
const coverageJsonSequential = './test-reports/monocart-report-sequential/coverage/coverage-final.json';
const outputDir = './test-reports/monocart-report/coverage';

console.log(`Merging coverage reports`);

const coverageParallel = JSON.parse(fs.readFileSync(coverageJsonParallel, 'utf8'));
const coverageSequential = JSON.parse(fs.readFileSync(coverageJsonSequential, 'utf8'));

const mapA = coverage.createCoverageMap(coverageParallel);
const mapB = coverage.createCoverageMap(coverageSequential);
mapA.merge(mapB);

const context = libReport.createContext({
    dir: outputDir,
    coverageMap: mapA,
});

const htmlReport = reports.create('html');
const lcovReport = reports.create('lcovonly', { file: 'lcov.info' });

htmlReport.execute(context);
lcovReport.execute(context);

console.log(`Merged coverage reports successfully`);

// Archive the lcov coverage report
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function createArchive(outputPath, inputDirectory) {
    const output = fs.createWriteStream(outputPath);
    const archive = archiver('zip', { zlib: { level: 9 } });

    output.on('close', () => {
        console.log(`Coverage report archived on: ${outputPath}`);
    });

    archive.on('error', (err) => {
        throw err;
    });

    archive.pipe(output);
    archive.directory(inputDirectory, false);
    await archive.finalize();
}

// Archiving process
const baseDir = path.join(__dirname, 'test-reports/monocart-report');
try {
    await createArchive(path.join(baseDir, 'e2e-client-coverage.zip'), path.join(baseDir, 'coverage'));

    await createArchive(path.join(baseDir, 'e2e-client-coverage-parallel.zip'), path.join(__dirname, 'test-reports/monocart-report-parallel'));

    await createArchive(path.join(baseDir, 'e2e-client-coverage-sequential.zip'), path.join(__dirname, 'test-reports/monocart-report-sequential'));
} catch (err) {
    console.error('Error while creating archives:', err);
}
