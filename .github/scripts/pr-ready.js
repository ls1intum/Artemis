/** Coalesce rapid PR updates before allocating build and self-hosted test runners. */
module.exports = async function checkPrReady({ github, context, core, now = Date.now, delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms)) }) {
    if (context.eventName !== 'pull_request') {
        core.setOutput('current', 'true');
        return;
    }
    const pull = context.payload.pull_request;
    if (!pull?.number || !pull.head?.sha) {
        throw new Error('The pull request number and expected head SHA are required');
    }
    const isCurrent = async () => {
        const { data } = await github.rest.pulls.get({ ...context.repo, pull_number: pull.number });
        return data.state === 'open' && data.head.sha === pull.head.sha;
    };
    if (!(await isCurrent())) {
        core.notice('This pull request revision has been superseded or closed; expensive jobs will not start.');
        core.setOutput('current', 'false');
        return;
    }
    if (context.payload.action === 'synchronize') {
        const { data } = await github.rest.actions.getWorkflowRun({ ...context.repo, run_id: context.runId });
        const created = Date.parse(data.created_at);
        if (!Number.isFinite(created)) {
            throw new Error('The workflow creation timestamp is invalid');
        }
        const remaining = Math.max(0, created + 90_000 - now());
        if (remaining > 0) {
            core.info(`Waiting ${Math.ceil(remaining / 1000)} seconds for rapid PR updates to settle on a GitHub-hosted runner.`);
            await delay(remaining);
        }
    }
    const current = await isCurrent();
    if (!current) {
        core.notice('A newer pull request revision arrived while this run was settling.');
    }
    core.setOutput('current', String(current));
};
