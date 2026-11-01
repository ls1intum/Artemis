/** Exact-source PR identity and secretless verification of maintainer-published fork analyses. */
const project = 'ls1intum_Artemis';
const shaPattern = /^[a-f0-9]{40}$/;
function checkedSha(value) {
    if (!shaPattern.test(value ?? '')) throw new Error('A full source commit SHA is required for Sonar analysis');
    return value;
}
function number(value) {
    if (!/^\d+$/.test(String(value)) || !Number.isSafeInteger(Number(value))) throw new Error('Invalid Sonar pull request number');
    return Number(value);
}
async function openPull(github, context, pullNumber, expectedSha) {
    const { data: pull } = await github.rest.pulls.get({ ...context.repo, pull_number: pullNumber });
    if (pull.state !== 'open' || !pull.head?.ref || !pull.base?.ref) throw new Error('Sonar requires a current open pull request');
    checkedSha(pull.head.sha);
    if (expectedSha && pull.head.sha !== expectedSha) throw new Error('The Sonar pull request revision has been superseded');
    if (context.eventName === 'pull_request' && pull.base.ref !== context.payload.pull_request.base.ref) {
        throw new Error('The pull request target changed; rerun CI for the current target');
    }
    return pull;
}
async function verifyFork({ pull, fetchImpl = fetch }) {
    const response = await fetchImpl('https://sonarcloud.io/api/project_pull_requests/list?project=' + project, {
        cache: 'no-store',
        signal: AbortSignal.timeout(30000),
    });
    if (!response.ok) throw new Error('Cannot verify the Sonar analysis: HTTP ' + response.status);
    const data = await response.json();
    if (!Array.isArray(data.pullRequests)) throw new Error('Invalid Sonar pull-request response');
    const matches = data.pullRequests.filter((entry) => String(entry.key) === String(pull.number));
    const entry = matches[0];
    if (
        matches.length !== 1 ||
        entry.commit?.sha !== pull.head.sha ||
        entry.branch !== pull.head.ref ||
        entry.target !== pull.base.ref ||
        typeof entry.analysisDate !== 'string' ||
        !/^\d{4}-\d{2}-\d{2}T/.test(entry.analysisDate) ||
        !Number.isFinite(Date.parse(entry.analysisDate)) ||
        entry.status?.qualityGateStatus !== 'OK'
    ) {
        throw new Error(
            'No passing Sonar quality gate exists for this exact PR head and target. A maintainer must review and promote this commit to chore/sonar/pr-' +
                pull.number +
                ', dispatch Sonar with pull_request_number=' +
                pull.number +
                ', then rerun this Sonar verification job.',
        );
    }
}
async function prepareAnalysis({ github, context, core, requestedSha, pullRequest = 0, defaultBranch = false, allowScan = true, fetchImpl }) {
    const manual = context.eventName === 'workflow_dispatch';
    if (context.eventName !== 'pull_request' && !allowScan) throw new Error('A Sonar token is required to perform this analysis');
    const pullNumber = context.eventName === 'pull_request' ? number(context.payload.pull_request.number) : manual ? number(pullRequest) : 0;
    if (context.eventName === 'pull_request' && pullNumber === 0) throw new Error('A positive pull request number is required');
    if (pullNumber && defaultBranch) throw new Error('PR analysis and default-branch recovery cannot be combined');
    let sha = requestedSha || context.sha;
    let branch = context.ref.replace(/^refs\/heads\//, '');
    let pull;
    let scan = true;
    if (pullNumber) {
        pull = await openPull(github, context, pullNumber, manual ? undefined : checkedSha(requestedSha || context.payload.pull_request.head.sha));
        if (manual) {
            // Promotion is a maintainer trust decision. Never automatically check out a fork head with secrets.
            const ref = await github.rest.git.getRef({ ...context.repo, ref: 'heads/chore/sonar/pr-' + pullNumber });
            if (ref.data.object.sha !== pull.head.sha) throw new Error('The internal Sonar promotion branch must point to the current exact PR head');
            sha = ref.data.object.sha;
            core.notice('Scanning maintainer-promoted commit ' + sha + ' under original PR ' + pullNumber);
        } else {
            sha = pull.head.sha;
            if (pull.head.repo?.full_name !== context.repo.owner + '/' + context.repo.repo || !allowScan) {
                await verifyFork({ pull, fetchImpl });
                scan = false;
                core.notice('Verified the passing Sonar gate for this exact PR source and target without executing source code or exposing a token.');
            }
        }
        branch = pull.head.ref;
    } else if (manual && defaultBranch) {
        const ref = await github.rest.repos.getBranch({ ...context.repo, branch: context.payload.repository.default_branch });
        sha = ref.data.commit.sha;
        branch = context.payload.repository.default_branch;
    }
    checkedSha(sha);
    for (const [key, value] of Object.entries({
        sha,
        scan: String(scan),
        pull_request: String(pullNumber),
        branch,
        base: pull?.base.ref || '',
    }))
        core.setOutput(key, value);
    return { sha, scan, pullNumber, branch, base: pull?.base.ref || '' };
}
async function findPrCoverage({ github, context, sha }) {
    const options = { ...context.repo, per_page: 100 };
    const runs = await github.paginate(github.rest.actions.listWorkflowRuns, { ...options, workflow_id: 'ci.yml', head_sha: checkedSha(sha) });
    for (const run of runs) {
        if (run.head_sha !== sha) continue;
        const jobs = await github.paginate(github.rest.actions.listJobsForWorkflowRun, { ...options, run_id: run.id, filter: 'latest' });
        const tests = jobs.filter((job) => job.name.startsWith('Test / '));
        if (!tests.length || !tests.every((job) => ['success', 'skipped'].includes(job.conclusion))) continue;
        const artifacts = await github.paginate(github.rest.actions.listWorkflowRunArtifacts, { ...options, run_id: run.id });
        if (coverageArtifacts(jobs, artifacts).size) {
            return String(run.id);
        }
    }
    throw new Error('No successful exact-source test run with coverage artifacts exists for this promoted PR');
}
function coverageArtifacts(jobs, artifacts) {
    const tests = jobs.filter((job) => job.name.startsWith('Test / '));
    if (!tests.length || !tests.every((job) => ['success', 'skipped'].includes(job.conclusion))) return new Set();
    const succeeded = (name) => tests.some((job) => job.name === name && job.conclusion === 'success');
    const shards = tests.filter((job) => job.name.startsWith('Test / Server Tests ('));
    const serverExecuted = shards.length > 0 && shards.every((job) => job.conclusion === 'success');
    const eligible = new Set();
    for (const artifact of artifacts.filter((item) => !item.expired)) {
        if (['Vitest Coverage Report', 'Sonar Client Coverage'].includes(artifact.name) && succeeded('Test / Client Tests')) eligible.add(artifact.name);
        if (artifact.name === 'Sonar Server Coverage' && serverExecuted) eligible.add(artifact.name);
        if (artifact.name === 'Server JaCoCo XML' && serverExecuted && shards.length === 7 && succeeded('Test / Server Tests Report')) eligible.add(artifact.name);
    }
    if (serverExecuted && !eligible.has('Sonar Server Coverage') && !eligible.has('Server JaCoCo XML')) return new Set();
    return eligible;
}
module.exports = { prepareAnalysis, verifyFork, findPrCoverage, coverageArtifacts };
