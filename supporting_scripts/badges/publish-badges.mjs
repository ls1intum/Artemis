import { execFileSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

/** Only used in the isolated badges-branch checkout. Never force-push generated state. */
export function publishBadges(directory, files, run = (args) => execFileSync('git', args, { cwd: directory, stdio: 'pipe' })) {
    if (!files.length || files.some((file) => !/^[\w-]+\.(json|svg)$/.test(file))) throw new Error('Invalid badge filenames');
    run(['config', 'user.name', 'github-actions[bot]']);
    run(['config', 'user.email', '41898282+github-actions[bot]@users.noreply.github.com']);
    run(['add', '--', ...files]);
    if (
        !run(['diff', '--cached', '--name-only', '--', ...files])
            .toString()
            .trim()
    )
        return false;
    run(['commit', '-m', 'Update README badges', '--', ...files]);
    for (let attempt = 0; attempt < 3; attempt += 1) {
        try {
            run(['push', 'origin', 'HEAD:badges']);
            return true;
        } catch (error) {
            if (attempt === 2) throw error;
            // Coverage and release publishers can overlap. Replay our small commit over the
            // other writer; a genuine conflict fails safely and retains the remote badges.
            run(['fetch', 'origin', 'badges']);
            run(['rebase', 'origin/badges']);
        }
    }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    try {
        const [, , directory, ...files] = process.argv;
        if (!directory) throw new Error('Usage: publish-badges.mjs <badges-directory> <file>...');
        console.log(publishBadges(directory, files) ? 'Published README badges.' : 'README badges unchanged.');
    } catch (error) {
        console.error(`Could not publish badges; the published values stand. ${error.message}`);
        process.exitCode = 1;
    }
}
