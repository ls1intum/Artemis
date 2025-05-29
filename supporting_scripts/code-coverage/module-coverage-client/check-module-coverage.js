const path = require('path');
const fs   = require('fs');

const summaryPath = path.resolve(
    __dirname,
    '../../../build/test-results/coverage-summary.json'
);
if (!fs.existsSync(summaryPath)) {
    console.error('❌ coverage-summary.json not found at', summaryPath);
    process.exit(1);
}
const summary = require(summaryPath);


const moduleThresholds = {
    assessment:    { statements: 89.14, branches: 75.20, functions: 83.01, lines: 89.22 },
    atlas:         { statements: 89.14, branches: 75.20, functions: 83.01, lines: 89.22 },
    core:           { statements: 89.14, branches: 75.20, functions: 83.01, lines: 89.22 },
    exercise:       { statements: 89.14, branches: 75.20, functions: 83.01, lines: 89.22 },
    programming:    { statements: 89.14, branches: 75.20, functions: 83.01, lines: 89.22 },
    quiz:           { statements: 89.14, branches: 75.20, functions: 83.01, lines: 89.22 },
    fileupload:  { statements: 89.14, branches: 75.20, functions: 83.01, lines: 89.22 },
    text:           { statements: 89.14, branches: 75.20, functions: 83.01, lines: 89.22 },
    modeling:       { statements: 89.14, branches: 75.20, functions: 83.01, lines: 89.22 },
    communication:  { statements: 89.14, branches: 75.20, functions: 83.01, lines: 89.22 },
    plagiarism:     { statements: 89.14, branches: 75.20, functions: 83.01, lines: 89.22 },
    iris:           { statements: 89.14, branches: 75.20, functions: 83.01, lines: 89.22 },
    lti:            { statements: 89.14, branches: 75.20, functions: 83.01, lines: 89.22 },
    tutorialgroup:  { statements: 89.14, branches: 75.20, functions: 83.01, lines: 89.22 },
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
        const pct = total > 0 ? (covered/total)*100 : 0;
        const pass = pct >= thresholds[metric];
        console.log(`  ${pass ? '✅' : '❌'} ${metric.padEnd(10)} : ${pct.toFixed(2)}%  (need ≥ ${thresholds[metric]}%)`);
        if (!pass) failed = true;
    }
}

process.exit(failed ? 1 : 0);
