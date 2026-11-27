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

// renovate: datasource=npm depName=knip
const KNIP_VERSION = '6.44.1';

const result = spawnSync('pnpm', ['dlx', `knip@${KNIP_VERSION}`, ...process.argv.slice(2)], { stdio: 'inherit' });
if (result.error) {
    console.error(`Could not run knip: ${result.error.message}`);
    process.exit(1);
}
process.exit(result.status ?? 1);
