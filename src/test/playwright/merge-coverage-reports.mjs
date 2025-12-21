// merge-coverage-reports.mjs
/* global console, process */
import path from 'path';
import { fileURLToPath } from 'url';
import archiver from 'archiver';
import coverage from 'istanbul-lib-coverage';
import reports from 'istanbul-reports';
import libReport from 'istanbul-lib-report';
import fsAsync from 'fs/promises';
import fs from 'fs';

/**
 * Represents a configuration object used for filtering file paths during coverage report generation.
 *
 * @property {string[]} includePaths - An array of file path patterns to include in the coverage analysis.
 * @property {string[]} excludePaths - An array of file path patterns to exclude from the coverage analysis.
 */
const coverageFilters = {
    includePaths: ['src/main/webapp'],
    excludePaths: [],
};

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const testReportsDir = path.join(__dirname, 'test-reports');
const coverageDir = path.join(testReportsDir, 'client-coverage')
const lcovDir = path.join(coverageDir, 'lcov-report');

console.log(`Merging coverage reports`);

// Create a new combined coverage map
let combinedMap = coverage.createCoverageMap({});

if (fs.existsSync(testReportsDir)) {
    const reportDirs = fs
        .readdirSync(testReportsDir, { withFileTypes: true })
        .filter((d) => d.isDirectory() && d.name.startsWith('monocart-report-'))
        .map((d) => path.join(testReportsDir, d.name));

    if (reportDirs.length === 0) {
        console.log('No monocart coverage report directories found, skipping');
    }

    for (const reportDir of reportDirs) {
        const coveragePath = path.join(reportDir, 'coverage', 'coverage-final.json');
        if (!fs.existsSync(coveragePath)) {
            continue;
        }

        try {
            const coverageData = JSON.parse(fs.readFileSync(coveragePath, 'utf8'));
            const filteredCoverageData = filterCoverageData(coverageData, coverageFilters);
            const map = coverage.createCoverageMap(filteredCoverageData);
            combinedMap.merge(map);
            console.log(`Loaded coverage report: ${path.basename(reportDir)}`);
        } catch (err) {
            console.error(`Error loading coverage report from ${reportDir}:`, err.message);
        }
    }
}

// Ensure a coverage directory exists
if (!fs.existsSync(coverageDir)) {
    fs.mkdirSync(coverageDir, { recursive: true });
}

// Create and generate reports
const context = libReport.createContext({
    dir: lcovDir,
    coverageMap: combinedMap,
});

const htmlReport = reports.create('html');
const lcovReport = reports.create('lcovonly', { file: 'lcov.info' });

htmlReport.execute(context);
lcovReport.execute(context);

console.log(`Merged coverage reports successfully`);

// Clean up all monocart report directories
if (fs.existsSync(testReportsDir)) {
    const reportDirs = fs
        .readdirSync(testReportsDir, { withFileTypes: true })
        .filter((d) => d.isDirectory() && d.name.startsWith('monocart-report-'))
        .map((d) => path.join(testReportsDir, d.name));

    for (const reportDir of reportDirs) {
        await fsAsync.rm(reportDir, { recursive: true, force: true });
    }
}

// Bamboo can upload only files as an artifact, not directories
// That's why we archive the lcov coverage directory on CI to prepare it as an artifact
if (process.env.CI === 'true') {
    try {
        await createArchive(path.join(testReportsDir, 'e2e-client-coverage.zip'), lcovDir);
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

/**
 * Filter coverage data based on include and exclude path filters
 * @param {Object} coverageData - The coverage data to filter
 * @param {Object} filters - The filters to apply
 * @returns {Object} - The filtered coverage data
 */
function filterCoverageData(coverageData, filters) {
    Object.keys(coverageData).forEach(filePath => {
        const shouldInclude = filters.includePaths.length === 0 ||
            filters.includePaths.some(includePath => filePath.includes(includePath));
        const shouldExclude = filters.excludePaths.some(excludePath => filePath.includes(excludePath));

        if (!(shouldInclude && !shouldExclude)) {
            delete coverageData[filePath];
        }
    });

    return coverageData;
}
