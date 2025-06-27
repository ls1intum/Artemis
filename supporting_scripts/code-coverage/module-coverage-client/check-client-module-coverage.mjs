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
        branches:   82.93,
        functions:  88.07,
        lines:      93.04,
    },
    communication: {
        statements: 92.08,
        branches:   77.31,
        functions:  88.21,
        lines:      92.38,
    },
    core: {
        statements: 89.05,
        branches:   70.89,
        functions:  80.70,
        lines:      89.06,
    },
    exam: {
        statements: 91.30,
        branches:   78.12,
        functions:  83.66,
        lines:      91.53,
    },
    exercise: {
        statements: 88.56,
        branches:   79.09,
        functions:  80.22,
        lines:      88.65,
    },
    fileupload: {
        statements: 92.59,
        branches:   78.48,
        functions:  84.80,
        lines:      93.23,
    },
    iris: {
        statements: 86.10,
        branches:   67.62,
        functions:  85.30,
        lines:      86.67,
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
        branches:   81.91,
        functions:  85.38,
        lines:      91.77,
    },
    programming: {
        statements: 88.72,
        branches:   76.53,
        functions:  80.88,
        lines:      88.83,
    },
    quiz: {
        statements: 86.45,
        branches:   74.79,
        functions:  79.01,
        lines:      86.56,
    },
    shared: {
        statements: 86.79,
        branches:   71.58,
        functions:  83.93,
        lines:      86.59,
    },
    text: {
        statements: 89.27,
        branches:   74.16,
        functions:  85.84,
        lines:      89.63,
    },
    tutorialgroup: {
        statements: 91.05,
        branches:   76.38,
        functions:  83.27,
        lines:      90.92,
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

