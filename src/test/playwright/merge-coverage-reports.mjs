// merge-coverage-reports.mjs
import path from 'path';
import { fileURLToPath } from 'url';
import archiver from 'archiver';
import coverage from 'istanbul-lib-coverage';
import reports from 'istanbul-reports';
import libReport from 'istanbul-lib-report';
import fsAsync from 'fs/promises';
import fs from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const coverageParallelDir = path.join(__dirname, 'test-reports/monocart-report-parallel');
const coverageSequentialDir = path.join(__dirname, 'test-reports/monocart-report-sequential');
const coverageDir = path.join(__dirname, 'test-reports/client-coverage');
const lcovDir = path.join(coverageDir, 'lcov-report');

console.log(`Merging coverage reports`);

const coverageParallel = JSON.parse(fs.readFileSync(path.join(coverageParallelDir, '/coverage/coverage-final.json'), 'utf8'));
const coverageSequential = JSON.parse(fs.readFileSync(path.join(coverageSequentialDir, '/coverage/coverage-final.json'), 'utf8'));

const mapA = coverage.createCoverageMap(coverageParallel);
const mapB = coverage.createCoverageMap(coverageSequential);
mapA.merge(mapB);

const context = libReport.createContext({
    dir: lcovDir,
    coverageMap: mapA,
});

const htmlReport = reports.create('html');
const lcovReport = reports.create('lcovonly', { file: 'lcov.info' });

htmlReport.execute(context);
lcovReport.execute(context);

console.log(`Merged coverage reports successfully`);

await fsAsync.rm(coverageParallelDir, { recursive: true, force: true });
await fsAsync.rm(coverageSequentialDir, { recursive: true, force: true });

// Bamboo can upload only files as an artifact, not directories
// That's why we archive the lcov coverage directory on CI to prepare it as an artifact
if (process.env.CI === 'true') {
    try {
        await createArchive(path.join(coverageDir, 'e2e-client-coverage.zip'), lcovDir);
    } catch (err) {
        console.error('Error while creating archives:', err);
    }
}

// Archives the directory
async function createArchive(outputPath, inputDirectory) {
    const output = await fs.createWriteStream(outputPath);
    const archive = archiver('zip', { zlib: { level: 9 } });

    output.on('close', () => {
        console.log(`Coverage report archived on: ${outputPath}`);
    });

    archive.on('error', (err) => {
        throw err;
    });

    archive.pipe(output);
    archive.directory(inputDirectory, '', (entry) => entry);
    await archive.finalize();
}
