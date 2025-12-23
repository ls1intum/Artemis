import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PROJECT_ROOT = path.resolve(__dirname, '../../..');

// Coverage file paths
const jestSummaryPath = path.resolve(PROJECT_ROOT, 'build/test-results/coverage-summary.json');
const vitestSummaryPath = path.resolve(PROJECT_ROOT, 'build/test-results/vitest/coverage/coverage-summary.json');

/**
 * Parse vitest.config.ts to extract module names from include patterns.
 * The vitest.config.ts is the single source of truth for which modules use Vitest.
 */
function getVitestModules() {
    const vitestConfigPath = path.join(PROJECT_ROOT, 'vitest.config.ts');
    if (!fs.existsSync(vitestConfigPath)) {
        return new Set();
    }
    const content = fs.readFileSync(vitestConfigPath, 'utf-8');
    // Match patterns like: 'src/main/webapp/app/fileupload/**/*.spec.ts'
    const modulePattern = /src\/main\/webapp\/app\/([a-zA-Z0-9_-]+)\/\*\*/g;
    const modules = new Set();
    let match;
    while ((match = modulePattern.exec(content)) !== null) {
        modules.add(match[1]);
    }
    return modules;
}

const VITEST_MODULES = getVitestModules();

// Load coverage files
let jestSummary = {};
let vitestSummary = {};

if (fs.existsSync(jestSummaryPath)) {
    try {
        jestSummary = JSON.parse(fs.readFileSync(jestSummaryPath, 'utf-8'));
    } catch (error) {
        console.error('❌ Failed to parse Jest coverage-summary.json:', error);
        process.exit(1);
    }
} else {
    console.error('❌ Jest coverage-summary.json not found at', jestSummaryPath);
    process.exit(1);
}

if (fs.existsSync(vitestSummaryPath)) {
    try {
        vitestSummary = JSON.parse(fs.readFileSync(vitestSummaryPath, 'utf-8'));
        console.log('✅ Vitest coverage loaded for modules:', [...VITEST_MODULES].join(', '));
    } catch (error) {
        console.error('❌ Failed to parse Vitest coverage-summary.json:', error);
        process.exit(1);
    }
} else if (VITEST_MODULES.size > 0) {
    console.error('❌ Vitest coverage-summary.json not found at', vitestSummaryPath);
    console.error('   Vitest modules require coverage. Run "npm run vitest:coverage" first.');
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
        statements: 91.50,
        branches:   75.80,
        functions:  84.60,
        lines:      91.80,
    },
    exercise: {
        statements: 88.40,
        branches:   76.60,
        functions:  80.20,
        lines:      88.50,
    },
    fileupload: {
        statements: 94.40,
        branches:   78.10,
        functions:  94.30,
        lines:      94.80,
    },
    hyperion: {
        // Currently, there are no files under src/main/webapp/app/hyperion/,
        // so thresholds mirror the current effective coverage (no files found → skipped by checker).
        // Once client-side Hyperion code exists, update these to the measured coverage.
        statements: 0,
        branches:   0,
        functions:  0,
        lines:      0,
    },
    iris: {
        statements: 88.5,
        branches:   76.5,
        functions:  84.0,
        lines:      88.8,
    },
    lecture: {
        statements: 92.50,
        branches:   75.50,
        functions:  88.50,
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

    // Use Vitest coverage for Vitest modules, Jest for everything else
    const summary = VITEST_MODULES.has(module) ? vitestSummary : jestSummary;

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

    const testFramework = VITEST_MODULES.has(module) ? '[vitest]' : '[jest]';
    const moduleFailed = evaluateAndPrintMetrics(`${module} ${testFramework}`, aggregatedMetrics, thresholds);
    if (moduleFailed) {
        anyModuleFailed = true;
    }
}

process.exit(anyModuleFailed ? 1 : 0);
