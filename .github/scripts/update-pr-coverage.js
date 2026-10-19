// Render the coverage table into the PR description's `### Test Coverage` section (idempotent,
// anchored replace). Shared by ci.yml's in-run `coverage-report` job (internal PRs) and
// pullrequest-coverage-reporter.yml (fork PRs) so the rendering cannot drift.
//
// Env: PR_NUMBER, PR_AUTHOR, HAS_CLIENT, HAS_SERVER, COVERAGE_TABLE, COVERAGE_SUCCESS,
//      TEST_CONCLUSION, TEST_RUN_URL, POST_AUTHOR_COMMENT ('true' to also @-mention the author once
//      — the fork path sets it true; the high-frequency in-run path sets it false to avoid stacking
//      a fresh comment on every push, since the description update is already idempotent).
module.exports = async ({ github, context, core }) => {
    const prNumber = Number(process.env.PR_NUMBER);
    const author = process.env.PR_AUTHOR;
    const owner = context.repo.owner;
    const repo = context.repo.repo;

    const hasClient = process.env.HAS_CLIENT === 'true';
    const hasServer = process.env.HAS_SERVER === 'true';
    const coverageTable = (process.env.COVERAGE_TABLE || '').trim();
    const coverageSuccess = process.env.COVERAGE_SUCCESS === 'true';
    const testConclusion = process.env.TEST_CONCLUSION;
    const testRunUrl = process.env.TEST_RUN_URL;
    const postAuthorComment = process.env.POST_AUTHOR_COMMENT === 'true';

    const { data: pr } = await github.rest.pulls.get({ owner, repo, pull_number: prNumber });

    let body = pr.body || '';
    const coverageSection = '### Test Coverage';
    const coverageIndex = body.indexOf(coverageSection);
    if (coverageIndex === -1) {
        console.log('Test Coverage section not found in PR description');
        return;
    }
    const afterCoverage = body.substring(coverageIndex + coverageSection.length);
    const nextSectionMatch = afterCoverage.match(/\n###\s/);
    const nextSectionIndex = nextSectionMatch
        ? coverageIndex + coverageSection.length + nextSectionMatch.index
        : body.length;

    const timestamp = new Date().toISOString().replace('T', ' ').substring(0, 19) + ' UTC';

    const lines = [
        coverageSection,
        '<!-- Please add the test coverages for all changed files modified in this PR here. You can generate the coverage table using one of these options: -->',
        '<!-- 1. Run `pnpm run coverage:pr` to generate coverage locally by running only the affected module tests (see supporting_scripts/code-coverage/local-pr-coverage/README.md) -->',
        '<!-- 2. Use `supporting_scripts/code-coverage/generate_code_cov_table/generate_code_cov_table.py` to generate the table from CI artifacts (requires GitHub token, follow the README for setup) -->',
        '<!-- The line coverage must be above 90% for changed files, and you must use extensive and useful assertions for server tests and expect statements for client tests. -->',
        '<!-- Note: Confirm in the last column that you have implemented extensive assertions for server tests and expect statements for client tests. -->',
        '<!--       Remove rows with only trivial changes from the table. -->',
        ''
    ];

    const noChanges = !hasClient && !hasServer;
    if (noChanges) {
        lines.push('**No code changes detected** - test coverage not required for this PR.', '');
    } else if (!coverageSuccess) {
        lines.push(
            `**Warning:** Coverage table could not be generated. The \`Test\` job concluded with status \`${testConclusion}\`; coverage artifacts may be missing or incomplete. See [workflow logs](${testRunUrl}).`,
            ''
        );
    } else {
        if (testConclusion !== 'success') {
            lines.push(
                `**Note:** Some tests in the [Test job](${testRunUrl}) did not pass (\`${testConclusion}\`). Coverage below may be partial.`,
                ''
            );
        }
        if (coverageTable) {
            lines.push(coverageTable, '');
        } else {
            lines.push('Coverage data was found, but no rows applied to changed files.', '');
        }
    }
    lines.push(`_Last updated: ${timestamp}_`, '', '');

    const newBody = body.substring(0, coverageIndex) + lines.join('\n') + body.substring(nextSectionIndex);

    await github.rest.pulls.update({ owner, repo, pull_number: prNumber, body: newBody });
    console.log('Updated PR description with coverage results');

    // Notify the author (fork path only — the in-run path updates the description in place on every
    // push, so a fresh @-mention each time would be comment spam).
    if (!postAuthorComment || noChanges) return;
    const commentBody = coverageSuccess
        ? `@${author} Test coverage has been automatically updated in the PR description.`
        : `@${author} Coverage table could not be generated. See the [workflow run](${testRunUrl}) for details.`;
    await github.rest.issues.createComment({
        owner, repo, issue_number: prNumber, body: commentBody
    });
};
