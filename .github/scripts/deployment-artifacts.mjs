// Read files from the two Git objects without executing code from the target branch.
import { execFileSync } from 'node:child_process';
import { appendFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

export function deployImages(revision, cwd = process.cwd()) {
    const git = (...args) => execFileSync('git', args, { cwd, encoding: 'utf8' }).trim();
    const paths = git('ls-tree', '-r', '--name-only', revision, 'deployment/docker', 'docker').split('\n');
    const file = ['deployment/docker/artemis/Dockerfile', 'docker/artemis/Dockerfile'].find((candidate) => paths.includes(candidate));
    if (!file) throw new Error('No Artemis Dockerfile at ' + revision);
    const root = file.slice(0, -'/artemis/Dockerfile'.length);
    const contents = (path) => git('show', revision + ':' + path);
    const roleBuild = /^ARG ARTEMIS_ROLE[=\s]/m.test(contents(file));
    const compose = paths.filter((path) => path.startsWith(root + '/test-server-') && path.endsWith('.yml') && !path.endsWith('-aiworker.yml'));
    const files = compose.map(contents).join('\n');
    const specialized = files.includes('ghcr.io/ls1intum/artemis-core:');
    const agentImage = files.includes('ghcr.io/ls1intum/artemis-buildagent:');
    if (!compose.length || specialized !== agentImage || (specialized && !roleBuild)) throw new Error('Incomplete test-server topology at ' + revision);
    const roles = roleBuild ? ['universal', 'core', 'buildagent', 'aiworker'] : ['universal'];
    return {
        file,
        build: roles.map((role) => ({ image: role === 'universal' ? 'artemis' : 'artemis-' + role, args: roleBuild ? 'ARTEMIS_ROLE=' + role : '' })),
        verify: specialized ? ['artemis-core', 'artemis-buildagent'] : ['artemis'],
    };
}

// The installed Ansible helper checks out the branch tip. Compare the Compose
// files and runtime inputs it reads before pairing them with an older image.
export function sameTestServerConfiguration(first, second, cwd = process.cwd()) {
    if (first === second) return true;
    const diff = execFileSync('git', ['diff', '--name-only', first, second, '--', '.env', 'docker', 'deployment/docker'], { cwd, encoding: 'utf8' });
    const inputs =
        /^(?:deployment\/)?docker\/(?:test-server-[^/]*\.yml|(?:artemis|mysql|nginx|postgres|redis|broker-registry)\.yml|artemis\/config\/.*\.env|(?:nginx|mysql|postgres|registry)\/)/;
    return !diff.split('\n').some((path) => path === '.env' || inputs.test(path));
}

export function warArtifact(revision, cwd = process.cwd()) {
    const git = (...args) => execFileSync('git', args, { cwd, encoding: 'utf8' }).trim();
    const paths = git('ls-tree', '-r', '--name-only', revision, 'gradle', '.github/workflows').split('\n');
    const build = git('show', revision + ':build.gradle');
    const workflowPath = '.github/workflows/ci-build.yml';
    const workflow = paths.includes(workflowPath) ? git('show', revision + ':' + workflowPath) : '';
    if (paths.includes('gradle/core.gradle') || /\bbootCoreWar\b|\broleWars\b/.test(build) || workflow.includes('Artemis-role-wars')) {
        return { name: 'Artemis-role-wars', legacyFilename: '' };
    }
    const match = /^version\s*=\s*["']([0-9][0-9A-Za-z.+-]*)["']/m.exec(build);
    if (!match) throw new Error('Cannot determine the universal WAR filename at ' + revision);
    return { name: 'Artemis.war', legacyFilename: 'Artemis-' + match[1] + '.war' };
}

function main() {
    const [mode, revision, tip] = process.argv.slice(2);
    if (mode === 'wars') {
        const artifact = warArtifact(revision);
        appendFileSync(process.env.GITHUB_OUTPUT, `artifact=${artifact.name}\nlegacy_filename=${artifact.legacyFilename}\n`);
        return;
    }
    if (mode !== 'images') throw new Error('Expected wars or images');
    const target = deployImages(revision);
    const branch = revision === tip ? target : deployImages(tip);
    if (target.verify.join() !== branch.verify.join() || !sameTestServerConfiguration(revision, tip)) {
        throw new Error(
            'The branch tip has different test-server Compose files or runtime configuration than the requested commit. Select a rollback branch pointing to the requested revision, or deploy using the previous Compose checkout.',
        );
    }
    appendFileSync(
        process.env.GITHUB_OUTPUT,
        [
            'docker_file=' + target.file,
            'build_matrix=' + JSON.stringify({ include: target.build }),
            'server_image=' + target.verify[0],
            'role_topology=' + target.verify.includes('artemis-buildagent'),
            '',
        ].join('\n'),
    );
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main();
