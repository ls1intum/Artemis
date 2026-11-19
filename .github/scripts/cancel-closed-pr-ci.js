const cancellableStatuses = new Set(['queued', 'in_progress', 'requested', 'waiting', 'pending']);

module.exports = async function cancelClosedPrCi({ github, context, core }) {
    const pull = context.payload.pull_request;
    const closedAt = Date.parse(pull?.closed_at);
    const isCloseEvent = context.eventName === 'pull_request_target' && context.payload.action === 'closed';
    if (!isCloseEvent || !Number.isSafeInteger(pull?.number) || pull.number < 1 || !Number.isFinite(closedAt)) {
        throw new Error('Expected a pull request closure with a PR number and close time');
    }

    // The umbrella's PR-number group remains authoritative when pull_requests is empty,
    // including after a merge or when multiple PRs share a source branch or commit.
    let members;
    try {
        const { data } = await github.request('GET /repos/{owner}/{repo}/actions/concurrency_groups/{concurrency_group_name}', {
            ...context.repo,
            concurrency_group_name: `ci-pr-${pull.number}`,
            headers: { 'X-GitHub-Api-Version': '2026-03-10' },
        });
        members = data.group_members;
    } catch (error) {
        if (error.status === 404) {
            return; // GitHub reports inactive groups as not found.
        }
        throw error;
    }

    for (const member of members) {
        if (member.job_id != null) {
            continue;
        }
        const runParams = { ...context.repo, run_id: member.run_id };
        const { data: run } = await github.rest.actions.getWorkflowRun(runParams);
        // A run created in the close second may belong to a rapid reopen.
        const createdBeforeClose = Date.parse(run.created_at) < closedAt;
        if (run.path !== '.github/workflows/ci.yml' || run.event !== 'pull_request' || !cancellableStatuses.has(run.status) || !createdBeforeClose) {
            continue;
        }

        // Recheck after identifying the run. A delayed close event must not cancel reopened CI.
        const { data: currentPull } = await github.rest.pulls.get({ ...context.repo, pull_number: pull.number });
        if (currentPull.state !== 'closed' || currentPull.closed_at !== pull.closed_at) {
            return;
        }

        try {
            await github.rest.actions.cancelWorkflowRun(runParams);
            core.info(`Requested cancellation of CI run ${run.id} for closed PR #${pull.number}.`);
        } catch (error) {
            // A run can complete between checking its status and submitting the request.
            if (error.status !== 409 || (await github.rest.actions.getWorkflowRun(runParams)).data.status !== 'completed') {
                throw error;
            }
        }
    }
};
