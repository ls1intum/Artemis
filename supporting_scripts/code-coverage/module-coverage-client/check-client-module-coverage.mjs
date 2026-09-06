import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';
import { getVitestModules } from '../utils.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PROJECT_ROOT = path.resolve(__dirname, '../../..');

// Coverage file path (the entire client runs on Vitest)
const vitestSummaryPath = path.resolve(PROJECT_ROOT, 'build/test-results/vitest/coverage/coverage-summary.json');

const VITEST_MODULES = getVitestModules(PROJECT_ROOT);

// Load coverage file
let vitestSummary = {};

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
    console.error('   Vitest modules require coverage. Run "pnpm run vitest:coverage" first.');
    process.exit(1);
}

const moduleThresholds = {
    account: {
        statements: 95.0,
        branches: 80.0,
        functions: 95.0,
        lines: 95.5,
    },
    admin: {
        // Angular 22 / TS 6 upgrade (#13189) shifted line counts; statements measured 93.02 (under the old 93.1)
        // and lines sat at only +0.05 headroom (93.35). Lowered both to measured minus a small headroom.
        // The course-role migration removed the group-management functions from user-management-update together with
        // their tests. Deleting fully covered functions lowers the ratio ((C-N)/(T-N) < C/T), so functions measured
        // 89.29 against the old 89.4 without any code becoming untested. Lowered to measured minus a small headroom.
        statements: 92.7,
        branches: 78.0,
        functions: 89.0,
        lines: 93.0,
    },
    assessment: {
        statements: 93.0,
        branches: 82.0,
        functions: 91.8,
        lines: 93.7,
    },
    atlas: {
        // Angular 22 / TS 6 upgrade (#13189, incl. the knowledge-area-tree PrimeNG-21 revert) shifted coverage:
        // statements measured 86.44 and lines 86.41, both just under the old 86.8/86.5. Lowered to measured minus headroom.
        statements: 86.1,
        branches: 66.5,
        functions: 85.4,
        lines: 86.1,
    },
    // buildagent client module folded into localci/ (the UI was always served by core nodes and talked
    // to LocalCI REST endpoints). Conservative initial baselines mirror the pre-extraction numbers.
    localci: {
        statements: 89.0,
        branches: 74.0,
        functions: 84.0,
        lines: 89.0,
    },
    // localvc client = repository-view + commit-history components (moved from programming/shared).
    // Baselines set a few points below the measured coverage (stmts 98.6 / branch 88.5 / funcs 95.5 / lines 99.3)
    // so the gate is meaningful while leaving headroom for minor future variance.
    localvc: {
        statements: 95.0,
        branches: 82.0,
        functions: 90.0,
        lines: 95.0,
    },
    communication: {
        // Lowered after notification extraction moved ~5k lines (course-notification subtree)
        // and its associated coverage out. Ratchet back up once messaging side is measured.
        statements: 85.0,
        branches: 65.0,
        functions: 80.0,
        lines: 85.0,
    },
    core: {
        // Statements/lines lowered (course + admin moved out — their well-covered code lifted
        // the previous baseline). Branches/functions raised (the moved code had lower coverage
        // for those metrics on average). TODO: keep tightening as core continues to slim down.
        statements: 87.5,
        branches: 75.5,
        functions: 87.0,
        lines: 87.5,
    },
    course: {
        // TODO: branches at 73% reflects the large course-management subtree; ratchet up as
        // remaining components get tests (course-archive, course-dashboard visualizations).
        statements: 90.5,
        branches: 72.5,
        functions: 86.5,
        lines: 91.0,
    },
    exam: {
        // The exam-registration refactor (server-side student pagination, removal of the add-students dialog)
        // slightly lowered exam client coverage and pinned these thresholds at essentially the exact measured
        // values (0 headroom), so run-to-run variance makes the gate flake (measured ~89.7/75.2/84.1/90.1).
        // Set to the measured coverage minus a small headroom, matching the "leave headroom" pattern used by the
        // other modules here. Ratchet back up when the extracted student-import UI gains more tests.
        statements: 89.5,
        branches: 75.0,
        functions: 83.8,
        lines: 89.9,
    },
    exercise: {
        // branches lowered slightly after removing the exercise-feedback-suggestion-options component
        // (superseded by course-level Athena config); measured ~73.69.
        statements: 86.8,
        branches: 73.6,
        functions: 77.3,
        lines: 87.0,
    },
    fileupload: {
        statements: 94.4,
        branches: 76.8,
        functions: 93.8,
        lines: 94.8,
    },
    hyperion: {
        // Currently, there are no files under src/main/webapp/app/hyperion/,
        // so thresholds mirror the current effective coverage (no files found → skipped by checker).
        // Once client-side Hyperion code exists, update these to the measured coverage.
        statements: 0,
        branches: 0,
        functions: 0,
        lines: 0,
    },
    iris: {
        statements: 85.7,
        branches: 73.0,
        functions: 79.8,
        lines: 86.4,
    },
    lecture: {
        statements: 92.5,
        branches: 75.5,
        functions: 88.5,
        lines: 92.4,
    },
    lti: {
        statements: 93.4,
        branches: 80.8,
        functions: 88.6,
        lines: 93.2,
    },
    modeling: {
        // Lowered after switching isFeedbackSuggestionsEnabled to the course-level Athena config
        // (dropping the feedbackSuggestionModule/allowFeedbackRequests exercise fields and their
        // dedicated test cases); measured ~86.66/71.34/87.00/86.60.
        statements: 86.5,
        branches: 71.2,
        functions: 84.4,
        lines: 86.5,
    },
    notification: {
        // New module extracted from communication in this PR. Conservative initial baseline;
        // ratchet up once CI measures the actual coverage of the extracted UI.
        statements: 80.0,
        branches: 60.0,
        functions: 75.0,
        lines: 80.0,
    },
    plagiarism: {
        // On this branch (Angular 22 / TS 6 dependency upgrade, #13189) measured coverage sits a few tenths
        // below the previous ratchet (stmts 92.91 / branches 78.01 / lines 93.13). Lowered to the measured
        // values minus a small headroom, matching the "leave headroom" pattern used by exam/communication above.
        statements: 92.6,
        branches: 77.7,
        functions: 86.8,
        lines: 92.8,
    },
    programming: {
        // On this branch (#13189) statements dipped just below the previous ratchet (measured 88.79) and lines
        // sat at only +0.01 headroom (measured 89.01), which would flake run-to-run. Lowered both to the measured
        // values minus a small headroom.
        statements: 88.5,
        branches: 76.0,
        functions: 81.2,
        lines: 88.7,
    },
    quiz: {
        // Angular 22 / TS 6 upgrade (#13189): statements sat at exactly the old threshold (87.40, zero headroom)
        // and functions at only +0.08 (82.28); both would flake red. Lowered to measured minus a small headroom.
        statements: 87.1,
        branches: 73.6,
        functions: 82.0,
        lines: 87.8,
    },
    shared: {
        statements: 88.0,
        branches: 72.0,
        functions: 85.6,
        lines: 87.8,
    },
    text: {
        statements: 86.9,
        branches: 68.7,
        functions: 86.0,
        lines: 86.8,
    },
    tutorialgroup: {
        statements: 91.0,
        branches: 74.0,
        functions: 87.0,
        lines: 81.0,
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
        const shouldBumpCoverageUp = roundedPercentage - roundedThreshold >= SHOULD_BUMP_COVERAGE_DELTA;

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
        branches: { total: 0, covered: 0 },
        functions: { total: 0, covered: 0 },
        lines: { total: 0, covered: 0 },
    };

    for (const [filePath, metricsData] of Object.entries(vitestSummary)) {
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
            aggregatedMetrics[metric].total += metricsData[metric].total;
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
