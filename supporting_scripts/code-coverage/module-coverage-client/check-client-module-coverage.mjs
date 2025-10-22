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
        statements: 89.93,
        branches:   80.77,
        functions:  83.28,
        lines:      89.94,
    },
    atlas: {
        statements: 91.96,
        branches:   68.74,
        functions:  85.42,
        lines:      91.78,
    },
    buildagent: {
        statements: 93.16,
        branches:   83.13,
        functions:  88.07,
        lines:      93.04,
    },
    communication: {
        statements: 92.08,
        branches:   78.05,
        functions:  88.79,
        lines:      92.38,
    },
    core: {
        statements: 89.03,
        branches:   72.60,
        functions:  81.60,
        lines:      89.05,
    },
    exam: {
        statements: 91.30,
        branches:   78.12,
        functions:  83.66,
        lines:      91.53,
    },
    exercise: {
        statements: 88.52,
        branches:   79.00,
        functions:  80.20,
        lines:      88.63,
    },
    fileupload: {
        statements: 92.59,
        branches:   78.48,
        functions:  84.80,
        lines:      93.23,
    },
    iris: {
        statements: 86.90,
        branches:   70.88,
        functions:  84.67,
        lines:      87.46,
    },
    lecture: {
        statements: 92.19,
        branches:   79.42,
        functions:  86.93,
        lines:      92.30,
    },
    lti: {
        statements: 93.67,
        branches:   86.96,
        functions:  88.89,
        lines:      93.45,
    },
    modeling: {
        statements: 88.52,
        branches:   73.50,
        functions:  84.04,
        lines:      88.66,
    },
    plagiarism: {
        statements: 91.74,
        branches:   84.72,
        functions:  85.24,
        lines:      92.20,
    },
    programming: {
        statements: 89.10,
        branches:   77.10,
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
        statements: 86.74,
        branches:   71.49,
        functions:  83.88,
        lines:      86.53,
    },
    text: {
        statements: 89.32,
        branches:   74.75,
        functions:  86.04,
        lines:      89.63,
    },
    tutorialgroup: {
        statements: 91.31,
        branches:   76.70,
        functions:  83.70,
        lines:      91.20,
    },
};



const metrics = ['statements', 'branches', 'functions', 'lines'];

const AIMED_FOR_COVERAGE = 90;

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

        const status = `${higherThanExpected ? '⬆️' : ''} ${pass ? '✅' : '❌'}`;
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

