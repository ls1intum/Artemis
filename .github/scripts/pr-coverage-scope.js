// Read scope only from GitHub's metadata for the completed upstream run attempt. Fork artifacts
// remain data: no scope file is downloaded or executed, and a live PR's draft state is irrelevant.
function validationScope(jobs) {
    if (!Array.isArray(jobs)) return 'unavailable';
    const summaries = jobs.filter((job) => job?.name === 'CI Summary');
    if (summaries.length !== 1 || summaries[0].status !== 'completed' || !Array.isArray(summaries[0].steps)) return 'unavailable';
    if (summaries[0].steps.some((step) => typeof step?.name !== 'string')) return 'unavailable';
    const markers = summaries[0].steps.filter((step) => typeof step?.name === 'string' && step.name.startsWith('Test validation scope'));
    if (markers.length === 0) return null; // Legacy runs predate the marker; their artifacts can still establish full coverage.
    if (markers.length !== 1) return 'unavailable';
    const marker = markers[0];
    const match = /^Test validation scope: (deferred|affected|full|unavailable)$/.exec(marker.name);
    if (!match || match[0] !== marker.name || marker.status !== 'completed' || marker.conclusion !== 'success') return 'unavailable';
    return match[1];
}

module.exports = async ({ github, context, core }) => {
    let scope = 'unavailable';
    try {
        const run = context.payload.workflow_run;
        if (!Number.isSafeInteger(run.id) || run.id <= 0 || !Number.isSafeInteger(run.run_attempt) || run.run_attempt <= 0) {
            throw new Error('Missing upstream run or attempt identity');
        }
        const jobs = await github.paginate(github.rest.actions.listJobsForWorkflowRunAttempt, {
            owner: context.repo.owner,
            repo: context.repo.repo,
            run_id: run.id,
            attempt_number: run.run_attempt,
            per_page: 100,
        });
        const upstreamScope = validationScope(jobs);
        if (upstreamScope !== null && upstreamScope !== 'full') {
            scope = upstreamScope;
        } else {
            const artifacts = await github.paginate(github.rest.actions.listWorkflowRunArtifacts, {
                owner: context.repo.owner,
                repo: context.repo.repo,
                run_id: run.id,
                per_page: 100,
            });
            const names = new Set(artifacts.filter((artifact) => artifact?.expired === false).map((artifact) => artifact.name));
            const required = [];
            if (process.env.HAS_CLIENT === 'true') required.push('Client Coverage Summaries');
            if (process.env.HAS_SERVER === 'true') required.push('Server JaCoCo XML');
            // An explicit full marker never substitutes for complete, unexpired full-suite artifacts.
            if (run.conclusion === 'success' && required.every((name) => names.has(name))) scope = 'full';
        }
    } catch (error) {
        core.warning(`Could not determine upstream test scope: ${error.message}`);
    }
    core.setOutput('test_scope', scope);
    if (scope === 'unavailable') core.info('Full-suite coverage is unavailable or incomplete; omit the coverage table.');
};
