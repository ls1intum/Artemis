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
        statements: 89.90,
        branches:   80.70,
        functions:  83.20,
        lines:      89.90,
    },
    atlas: {
        statements: 91.50,
        branches:   67.90,
        functions:  84.60,
        lines:      91.40,
    },
    buildagent: {
        statements: 93.00,
        branches:   83.60,
        functions:  87.20,
        lines:      92.90,
    },
    communication: {
        statements: 92.50,
        branches:   78.40,
        functions:  89.10,
        lines:      92.80,
    },
    core: {
        statements: 89.70,
        branches:   72.60,
        functions:  81.60,
        lines:      89.70,
    },
    exam: {
        statements: 91.64,
        branches:   78.74,
        functions:  84.57,
        lines:      91.89,
    },
    exercise: {
        statements: 88.50,
        branches:   79.00,
        functions:  80.20,
        lines:      88.60,
    },
    fileupload: {
        statements: 92.50,
        branches:   78.40,
        functions:  84.70,
        lines:      93.20,
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
        statements: 87.70,
        branches:   73.10,
        functions:  86.20,
        lines:      88.30,
    },
    lecture: {
        statements: 91.70,
        branches:   79.30,
        functions:  86.20,
        lines:      91.70,
    },
    lti: {
        statements: 93.60,
        branches:   87.20,
        functions:  88.80,
        lines:      93.40,
    },
    modeling: {
        statements: 89.10,
        branches:   73.80,
        functions:  84.60,
        lines:      89.20,
    },
    plagiarism: {
        statements: 93.50,
        branches:   86.60,
        functions:  87.70,
        lines:      93.60,
    },
    programming: {
        statements: 89.10,
        branches:   77.00,
        functions:  81.40,
        lines:      89.20,
    },
    quiz: {
        statements: 87.70,
        branches:   75.40,
        functions:  81.40,
        lines:      87.90,
    },
    shared: {
        statements: 86.90,
        branches:   71.10,
        functions:  84.50,
        lines:      86.70,
    },
    text: {
        statements: 89.40,
        branches:   74.90,
        functions:  86.20,
        lines:      89.70,
    },
    tutorialgroup: {
        statements: 91.40,
        branches:   76.70,
        functions:  83.70,
        lines:      91.20,
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

