import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const summaryPath = path.resolve(
    __dirname,
    '../../../build/test-results/coverage-summary.json'
);
if (!fs.existsSync(summaryPath)) {
    console.error('❌ coverage-summary.json not found at', summaryPath);
    process.exit(1);
}

let summary;
try {
 summary = JSON.parse(fs.readFileSync(summaryPath, 'utf-8'));
} catch (error) {
    console.error('❌ Failed to parse coverage-summary.json:', error);
    process.exit(1);
}

const moduleThresholds = {
    assessment: {
        statements: 90.00,
        branches:   78.20,
        functions:  83.30,
        lines:      90.10,
    },
    atlas: {
        statements: 91.30,
        branches:   67.10,
        functions:  84.70,
        lines:      91.20,
    },
    buildagent: {
        statements: 92.00,
        branches:   73.60,
        functions:  85.10,
        lines:      92.10,
    },
    communication: {
        statements: 92.40,
        branches:   74.10,
        functions:  89.50,
        lines:      92.70,
    },
    core: {
        statements: 89.90,
        branches:   70.80,
        functions:  81.70,
        lines:      89.90,
    },
    exam: {
        statements: 91.60,
        branches:   75.80,
        functions:  84.60,
        lines:      91.90,
    },
    exercise: {
        statements: 88.40,
        branches:   76.60,
        functions:  80.20,
        lines:      88.50,
    },
    fileupload: {
        statements: 92.40,
        branches:   77.00,
        functions:  84.60,
        lines:      93.00,
    },
    hyperion: {
        // Currently, there are no files under src/main/webapp/app/hyperion/ in this branch,
        // so thresholds mirror the current effective coverage (no files found → skipped by checker).
        // Once client-side Hyperion code exists, update these to the measured coverage.
        statements: 0,
        branches:   0,
        functions:  0,
        lines:      0,
    },
    iris: {
        statements: 88.8,
        branches:   77.70,
        functions:  84.6,
        lines:      89.1,
    },
    lecture: {
        statements: 92.50,
        branches:   76.25,
        functions:  88.30,
        lines:      92.40,
    },
    lti: {
        statements: 93.40,
        branches:   80.80,
        functions:  88.60,
        lines:      93.20,
    },
    modeling: {
        statements: 89.00,
        branches:   73.00,
        functions:  84.40,
        lines:      89.20,
    },
    plagiarism: {
        statements: 93.40,
        branches:   81.90,
        functions:  87.50,
        lines:      93.50,
    },
    programming: {
        statements: 89.40,
        branches:   76.00,
        functions:  81.20,
        lines:      89.40,
    },
    quiz: {
        statements: 88.80,
        branches:   72.10,
        functions:  82.70,
        lines:      89.00,
    },
    shared: {
        statements: 88.00,
        branches:   72.00,
        functions:  85.60,
        lines:      87.80,
    },
    text: {
        statements: 89.70,
        branches:   69.00,
        functions:  86.00,
        lines:      90.00,
    },
    tutorialgroup: {
        statements: 92.10,
        branches:   72.90,
        functions:  84.50,
        lines:      92.00,
    },
};

const metrics = ['statements', 'branches', 'functions', 'lines'];

const AIMED_FOR_COVERAGE = 90;
/**
 * If the coverage is >= this value higher than the threshold, an upward arrow is shown to indicate the threshold should be bumped up.
 */
const SHOULD_BUMP_COVERAGE_DELTA = 0.1;

const roundToTwoDigits = (value) => Math.round(value * 100) / 100;

const evaluateAndPrintMetrics = (module, aggregatedMetrics, thresholds) => {
    let failed = false;
    console.log(`\nModule: ${module}`);
    for (const metric of metrics) {
        const { total, covered } = aggregatedMetrics[metric];
        const percentage = total > 0 ? (covered / total) * 100 : 0;
        const roundedPercentage = roundToTwoDigits(percentage);
        const roundedThreshold = roundToTwoDigits(thresholds[metric]);
        const pass = roundedPercentage >= roundedThreshold;
        const higherThanExpected = roundedPercentage > roundedThreshold && roundedThreshold < AIMED_FOR_COVERAGE;
        const shouldBumpCoverageUp = (roundedPercentage - roundedThreshold) >= SHOULD_BUMP_COVERAGE_DELTA;

        const status = `${higherThanExpected && shouldBumpCoverageUp ? '⬆️' : ''} ${pass ? '✅' : '❌'}`;
        console.log(`${status.padStart(6)} ${metric.padEnd(12)}: ${roundedPercentage.toFixed(2).padStart(6)}%  (need ≥ ${roundedThreshold.toFixed(2)}%)`);
        if (!pass) failed = true;
    }
    return failed;
};

let anyModuleFailed = false;

for (const [module, thresholds] of Object.entries(moduleThresholds)) {
    const prefix = `src/main/webapp/app/${module}/`;
    const aggregatedMetrics = {
        statements: { total: 0, covered: 0 },
        branches:   { total: 0, covered: 0 },
        functions:  { total: 0, covered: 0 },
        lines:      { total: 0, covered: 0 },
    };

    for (const [filePath, metricsData] of Object.entries(summary)) {
        if (filePath === 'total') continue;
        if (!filePath.includes(prefix)) continue;
        if (!metricsData || typeof metricsData !== 'object') {
            console.warn(`⚠️  Invalid coverage data for file: ${filePath}`);
            continue;
            }
        for (const metric of metrics) {
            if (!metricsData[metric] || typeof metricsData[metric].total !== 'number' || typeof metricsData[metric].covered !== 'number') {
                console.error(`❌  Missing or invalid ${metric} data for file: ${filePath}`);
                continue;
            }
            aggregatedMetrics[metric].total   += metricsData[metric].total;
            aggregatedMetrics[metric].covered += metricsData[metric].covered;
        }
    }

    if (aggregatedMetrics.statements.total === 0) {
        console.warn(`⚠️  no files found for module "${module}" (looking for "${prefix}")`);
        continue;
    }

    const moduleFailed = evaluateAndPrintMetrics(module, aggregatedMetrics, thresholds);
    if (moduleFailed) {
        anyModuleFailed = true;
    }

}
process.exit(anyModuleFailed ? 1 : 0);

