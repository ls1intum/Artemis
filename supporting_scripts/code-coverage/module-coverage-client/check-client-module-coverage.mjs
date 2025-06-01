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

const summary = JSON.parse(fs.readFileSync(summaryPath, 'utf-8'));

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
        branches:   77.28,
        functions:  88.21,
        lines:      92.38,
    },
    core: {
        statements: 88.98,
        branches:   70.76,
        functions:  80.75,
        lines:      89.01,
    },
    exam: {
        statements: 91.31,
        branches:   78.07,
        functions:  83.65,
        lines:      91.54,
    },
    exercise: {
        statements: 88.49,
        branches:   78.98,
        functions:  80.22,
        lines:      88.58,
    },
    fileupload: {
        statements: 92.57,
        branches:   76.58,
        functions:  84.62,
        lines:      93.21,
    },
    iris: {
        statements: 86.09,
        branches:   67.62,
        functions:  85.30,
        lines:      86.66,
    },
    lecture: {
        statements: 92.19,
        branches:   79.31,
        functions:  86.91,
        lines:      92.30,
    },
    lti: {
        statements: 93.67,
        branches:   86.96,
        functions:  88.89,
        lines:      93.45,
    },
    modeling: {
        statements: 87.00,
        branches:   73.50,
        functions:  81.82,
        lines:      87.19,
    },
    plagiarism: {
        statements: 91.88,
        branches:   81.91,
        functions:  85.31,
        lines:      91.91,
    },
    programming: {
        statements: 88.67,
        branches:   76.47,
        functions:  80.86,
        lines:      88.79,
    },
    quiz: {
        statements: 86.22,
        branches:   74.75,
        functions:  78.74,
        lines:      86.34,
    },
    shared: {
        statements: 85.77,
        branches:   71.12,
        functions:  83.55,
        lines:      85.57,
    },
    text: {
        statements: 87.99,
        branches:   72.37,
        functions:  84.17,
        lines:      88.38,
    },
    tutorialgroup: {
        statements: 91.05,
        branches:   75.00,
        functions:  83.12,
        lines:      90.92,
    },
};



const metrics = ['statements', 'branches', 'functions', 'lines'];
let failed = false;

for (const [mod, thresholds] of Object.entries(moduleThresholds)) {
    const prefix = `src/main/webapp/app/${mod}/`;
    const agg = {
        statements: { total: 0, covered: 0 },
        branches:   { total: 0, covered: 0 },
        functions:  { total: 0, covered: 0 },
        lines:      { total: 0, covered: 0 },
    };

    for (const [filePath, m] of Object.entries(summary)) {
        if (filePath === 'total') continue;
        if (!filePath.includes(prefix)) continue;
        for (const metric of metrics) {
            agg[metric].total   += m[metric].total;
            agg[metric].covered += m[metric].covered;
        }
    }

    if (agg.statements.total === 0) {
        console.warn(`⚠️  no files found for module "${mod}" (looking for "${prefix}")`);
        continue;
    }

    console.log(`\nModule: ${mod}`);
    for (const metric of metrics) {
        const { total, covered } = agg[metric];
        const pct = total > 0 ? (covered / total) * 100 : 0;
        const threshold = thresholds[metric];
        const pass = Math.round(pct * 100) / 100 >= threshold;
        console.log(`  ${pass ? '✅' : '❌'} ${metric.padEnd(10)} : ${pct.toFixed(2)}%  (need ≥ ${threshold}%)`);
        if (!pass) failed = true;
    }
}

process.exit(failed ? 1 : 0);
