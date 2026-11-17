/** Enforce zero new duplicated lines using the already-published analysis for the exact PR head. */
const { verifyFork } = require('./sonar-pr.js');
const project = 'ls1intum_Artemis';
const metrics = ['new_duplicated_lines', 'new_duplicated_blocks', 'new_lines'];

function count(measures, metric) {
    const matches = measures.filter((measure) => measure.metric === metric);
    if (matches.length !== 1) throw new Error('Invalid or unavailable Sonar metric: ' + metric);
    const measure = matches[0];
    if (measure.periods !== undefined && !Array.isArray(measure.periods)) throw new Error('Invalid Sonar metric: ' + metric);
    const values = [measure.value, measure.period?.value, ...(measure.periods ?? []).map((period) => period.value)].filter((value) => value !== undefined);
    if (values.length !== 1 || typeof values[0] !== 'string' || !/^\d+$/.test(values[0]) || !Number.isSafeInteger(Number(values[0]))) {
        throw new Error('Invalid Sonar metric: ' + metric);
    }
    return Number(values[0]);
}

async function checkPullRequestDuplication({ github, context, sha, pullRequest, branch, base, summary, fetchImpl = fetch }) {
    if (!/^[a-f0-9]{40}$/.test(sha ?? '')) throw new Error('A full source commit SHA is required for the duplication guard');
    if (!/^\d+$/.test(String(pullRequest)) || !Number.isSafeInteger(Number(pullRequest)) || Number(pullRequest) <= 0) {
        throw new Error('A positive pull request number is required for the duplication guard');
    }
    const currentPull = async () => {
        const { data: pull } = await github.rest.pulls.get({ ...context.repo, pull_number: Number(pullRequest) });
        if (pull.number !== Number(pullRequest) || pull.state !== 'open' || pull.head?.sha !== sha || pull.head?.ref !== branch || pull.base?.ref !== base) {
            throw new Error('Duplication evidence must match the current open PR head and target');
        }
        return pull;
    };
    const pull = await currentPull();
    const before = await verifyFork({ pull, fetchImpl });
    const query = new URLSearchParams({ component: project, pullRequest: String(pullRequest), metricKeys: metrics.join(',') });
    const response = await fetchImpl('https://sonarcloud.io/api/measures/component?' + query, { cache: 'no-store', signal: AbortSignal.timeout(30000) });
    if (!response.ok) throw new Error('Cannot read Sonar duplication metrics: HTTP ' + response.status);
    const data = await response.json();
    if (data.component?.key !== project || data.component.pullRequest !== String(pullRequest) || !Array.isArray(data.component.measures))
        throw new Error('Invalid Sonar duplication response');
    const result = {
        duplicatedLines: count(data.component.measures, 'new_duplicated_lines'),
        duplicatedBlocks: count(data.component.measures, 'new_duplicated_blocks'),
        newLines: count(data.component.measures, 'new_lines'),
    };
    const after = await verifyFork({ pull: await currentPull(), fetchImpl });
    if (before.analysisDate !== after.analysisDate) throw new Error('Sonar analysis changed while reading duplication metrics; rerun verification');
    if (summary) {
        await summary
            .addHeading('PR duplication')
            .addTable([
                [
                    { data: 'Metric', header: true },
                    { data: 'Count', header: true },
                ],
                ['New duplicated lines', String(result.duplicatedLines)],
                ['New duplicated blocks', String(result.duplicatedBlocks)],
                ['New lines', String(result.newLines)],
            ])
            .write();
    }
    if (result.duplicatedLines > 0) {
        throw new Error(
            `This PR has ${result.duplicatedLines} newly duplicated line(s) in ${result.duplicatedBlocks} duplicated block(s). ` +
                'Share the repeated behavior before merging; the guard uses absolute counts even when the percentage rounds to 0.0%.',
        );
    }
    return result;
}

module.exports = { checkPullRequestDuplication };
