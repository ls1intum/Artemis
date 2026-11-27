/**
 * Runs knip, the dead code finder behind `pnpm run dead-code:client` and `pnpm run dead-code:client:report`.
 *
 * knip is not a dependency of the project: it is fetched on demand with `pnpm dlx` at the version below, so its packages
 * stay out of every install. All arguments are passed on to knip.
 *
 * Keep this on 6.44.1 until a later release is checked: 6.45.0 to 6.48.0 still report about twenty files as unused that
 * templates use (for example `safe-url.pipe.ts` and `virtual-scroll.component.ts`). To check a release, run
 * `pnpm dlx knip@<version> --include files`; it must report no unused file.
 */
import { spawnSync } from 'node:child_process';
import { basename } from 'node:path';

// renovate: datasource=npm depName=knip
const KNIP_VERSION = '6.44.1';

/**
 * Start the pnpm that runs this script, without a shell: a Corepack install on Windows only has `pnpm.cmd` shims, which Node
 * cannot start directly, and a shell would interpret the arguments that are forwarded to knip. A script that runs through pnpm
 * gets the path of pnpm in `npm_execpath`, either a JavaScript file (started with Node) or a program. Outside pnpm, fall back to
 * the `pnpm` on the path (with a shell on Windows, where it can only be a shim).
 */
function pnpmCommand() {
    const execPath = process.env.npm_execpath;
    if (execPath && basename(execPath).toLowerCase().startsWith('pnpm')) {
        return /\.[cm]?js$/.test(execPath) ? { command: process.execPath, prefix: [execPath], shell: false } : { command: execPath, prefix: [], shell: false };
    }
    return { command: 'pnpm', prefix: [], shell: process.platform === 'win32' };
}

const { command, prefix, shell } = pnpmCommand();
const result = spawnSync(command, [...prefix, 'dlx', `knip@${KNIP_VERSION}`, ...process.argv.slice(2)], { stdio: 'inherit', shell });
if (result.error) {
    console.error(`Could not run knip: ${result.error.message}`);
    process.exit(1);
}
process.exit(result.status ?? 1);
