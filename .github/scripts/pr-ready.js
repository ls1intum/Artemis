/** Reject obsolete PR heads and defer test suites while a pull request is a draft. */
module.exports = async function checkPrReady({ github, context, core }) {
    if (context.eventName !== 'pull_request') {
        core.setOutput('current', 'true');
        core.setOutput('tests_deferred', 'false');
        return;
    }
    const pull = context.payload.pull_request;
    if (!pull?.number || !pull.head?.sha || !pull.base?.ref) {
        throw new Error('The pull request number, expected head SHA and target branch are required');
    }
    const { data } = await github.rest.pulls.get({ ...context.repo, pull_number: pull.number });
    const current = data.state === 'open' && data.head.sha === pull.head.sha && data.base?.ref === pull.base.ref;
    if (!current) {
        core.notice('This pull request revision has been superseded or closed; expensive jobs will not start.');
    }
    core.setOutput('current', String(current));
    core.setOutput('tests_deferred', String(pull.draft === true));
};
