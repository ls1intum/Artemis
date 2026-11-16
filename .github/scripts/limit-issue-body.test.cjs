const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { limitIssueBody } = require('./limit-issue-body.cjs');
const runUrl = 'https://github.com/ls1intum/Artemis/actions/runs/123';

function assertBoundedReport(body) {
    assert.ok(Array.from(body).length <= 65536, 'GitHub must accept the issue body');
    assert.ok(body.includes(`[full report in the workflow logs](${runUrl})`));
}

test('keeps a short report unchanged', () => {
    const body = '# Broken links\n\n* https://example.org/missing — 404\n';
    assert.equal(limitIssueBody(body, runUrl), body);
});

test('bounds a large report and preserves a link to every finding', () => {
    const body = '# Broken links\n\n' + '* https://example.org/missing — 404\n'.repeat(3000);
    const limited = limitIssueBody(body, runUrl);
    assertBoundedReport(limited);
    assert.ok(limited.startsWith('# Broken links\n\n* https://example.org/missing — 404\n'));
});

test('truncates Unicode without producing a partial surrogate pair', () => {
    const limited = limitIssueBody('🔗'.repeat(70000), runUrl);
    assertBoundedReport(limited);
    assert.doesNotMatch(limited, /\p{Surrogate}/u);
});

test('the command rewrites the file consumed by gh issue', () => {
    const folder = fs.mkdtempSync(path.join(os.tmpdir(), 'artemis-docs-report-'));
    try {
        const reportPath = path.join(folder, 'body.md');
        const fullReport = '# Broken links\n' + 'x'.repeat(70000);
        fs.writeFileSync(reportPath, fullReport);
        const result = spawnSync(process.execPath, [path.join(__dirname, 'limit-issue-body.cjs'), reportPath, runUrl], { encoding: 'utf8' });
        assert.equal(result.status, 0, result.stderr);
        assertBoundedReport(fs.readFileSync(reportPath, 'utf8'));
        assert.equal(result.stdout, fullReport, 'the workflow log retains the complete report');
    } finally {
        fs.rmSync(folder, { recursive: true, force: true });
    }
});

test('the maintenance workflow passes a bounded report to gh while retaining the full source', () => {
    const folder = fs.mkdtempSync(path.join(os.tmpdir(), 'artemis-docs-workflow-'));
    try {
        const fullReport = '# External link failures\n' + '* https://example.org/missing — 404\n'.repeat(3000);
        fs.writeFileSync(path.join(folder, 'lychee.md'), fullReport);
        const capturePath = path.join(folder, 'posted-body.md');
        const ghPath = path.join(folder, 'gh');
        fs.writeFileSync(
            ghPath,
            `#!${process.execPath}\nconst fs = require('node:fs');\nconst args = process.argv.slice(2);\nif (args[0] !== 'issue') process.exit(1);\nif (args[1] === 'list') process.exit(0);\nif (args[1] !== 'create') process.exit(1);\nfs.copyFileSync(args[args.indexOf('--body-file') + 1], process.env.CAPTURE_PATH);\n`,
        );
        fs.chmodSync(ghPath, 0o755);
        const workflow = fs.readFileSync(path.join(__dirname, '../workflows/docs-maintenance.yml'), 'utf8');
        const externalJob = workflow.slice(workflow.indexOf('\n  external-links:'));
        const shell = externalJob.match(/        run: \|\n((?:          [^\n]*\n?)+)/)[1].replace(/^          /gm, '');
        const result = spawnSync('bash', ['-c', shell], {
            cwd: path.join(__dirname, '../..'),
            encoding: 'utf8',
            env: {
                ...process.env,
                PATH: `${folder}${path.delimiter}${process.env.PATH}`,
                RUNNER_TEMP: folder,
                CAPTURE_PATH: capturePath,
                GH_TOKEN: 'unused-test-token',
                TITLE: 'Documentation: external links that no longer work',
                LYCHEE_EXIT: '2',
                GITHUB_SERVER_URL: 'https://github.com',
                GITHUB_REPOSITORY: 'ls1intum/Artemis',
                GITHUB_RUN_ID: '123',
            },
        });
        assert.equal(result.status, 0, result.stderr);
        assertBoundedReport(fs.readFileSync(capturePath, 'utf8'));
        assert.equal(fs.readFileSync(path.join(folder, 'lychee.md'), 'utf8'), fullReport);
    } finally {
        fs.rmSync(folder, { recursive: true, force: true });
    }
});
