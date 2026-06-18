// Categorise a PR's changed files into client/server coverage-relevant sets via the GitHub API.
// Shared by ci.yml's in-run `coverage-report` job (internal PRs) and pullrequest-coverage-reporter.yml
// (fork PRs), so the two paths cannot drift. Reads PR_NUMBER from the env and sets the step outputs
// has_client_changes, has_server_changes, client_modules, server_modules, changed_files.
module.exports = async ({ github, context, core }) => {
    const prNumber = Number(process.env.PR_NUMBER);
    const files = await github.paginate(github.rest.pulls.listFiles, {
        owner: context.repo.owner, repo: context.repo.repo, pull_number: prNumber, per_page: 100,
    });
    const CLIENT = 'src/main/webapp/app/';
    const SERVER = 'src/main/java/de/tum/cit/aet/artemis/';
    const changed = [];
    const clientModules = new Set();
    const serverModules = new Set();
    let hasClient = false, hasServer = false;
    for (const f of files) {
        if (f.status === 'removed') continue; // deleted files have no coverage to report
        const p = f.filename;
        if (p.startsWith(CLIENT) && p.endsWith('.ts') && !p.endsWith('.spec.ts') && !p.endsWith('.module.ts')) {
            hasClient = true; changed.push(p);
            const rel = p.slice(CLIENT.length);
            if (rel.includes('/')) clientModules.add(rel.split('/')[0]);
        } else if (p.startsWith(SERVER) && p.endsWith('.java')) {
            hasServer = true; changed.push(p);
            serverModules.add(p.slice(SERVER.length).split('/')[0]);
        }
    }
    core.setOutput('has_client_changes', String(hasClient));
    core.setOutput('has_server_changes', String(hasServer));
    core.setOutput('client_modules', [...clientModules].join(','));
    core.setOutput('server_modules', [...serverModules].join(','));
    core.setOutput('changed_files', changed.join(','));
    console.log(`Client: ${hasClient} (${[...clientModules].join(',')}); Server: ${hasServer} (${[...serverModules].join(',')}); ${changed.length} coverage-relevant files`);
};
