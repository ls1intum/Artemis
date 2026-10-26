/**
 * Guards against unreviewed Vite overrides that can silently break the Angular dev server.
 *
 * The Angular dev server configures Vite's dependency pre-bundling through options tied to a
 * specific Vite version. Angular 22.1 moved that configuration from esbuild
 * (`optimizeDeps.esbuildOptions`) to rolldown (`optimizeDeps.rolldownOptions`), which only Vite 8
 * understands. When a `pnpm-workspace.yaml` override holds Vite on an older major, Vite silently
 * ignores the unknown option block — and with it the `angular-vite-optimize-deps` plugin that runs
 * the Angular linker over pre-bundled dependencies. Partially compiled Angular libraries then reach
 * the browser with their `ɵɵngDeclare*` calls intact and bootstrap dies with "The injectable
 * '_PlatformLocation' needs to be compiled using the JIT compiler, but '@angular/compiler' is not
 * available."
 *
 * Nothing about that failure points at the override: the production build never goes through Vite's
 * dependency optimizer, so only `pnpm start` and the E2E suites that run against it break. This
 * check turns that silent breakage into an actionable error at build time.
 *
 * One narrowly scoped security exception replaces builder 22.2.3's Vite 8.3.0 with 8.3.3.
 * All other combinations must still match the builder's exact pin; this does not allow arbitrary
 * patch upgrades, minor upgrades, or Vite 7 downgrades.
 */
import { createRequire } from 'node:module';

const EXACT_VERSION = /^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/;

/**
 * Verifies that Vite matches the builder's exact pin or the reviewed security exception.
 *
 * @param {object} [options]
 * @param {string} [options.resolveFrom] Directory to resolve `@angular/build` from. Defaults to
 *     this file's own location; tests point it at a synthetic `node_modules` tree.
 * @returns {{checked: true} | {checked: false, reason: string}} Whether a comparison actually
 *     happened, and if not, why it was skipped.
 * @throws {Error} When the resolved version differs from the declared one.
 */
export function checkViteOverride({ resolveFrom } = {}) {
    const require = resolveFrom ? createRequire(`${resolveFrom}/`) : createRequire(import.meta.url);

    let manifestPath;
    try {
        manifestPath = require.resolve('@angular/build/package.json');
    } catch {
        // A broken or incomplete install fails loudly a moment later in the build itself.
        return { checked: false, reason: '@angular/build is not installed' };
    }

    const angularBuild = require(manifestPath);
    const expected = angularBuild.dependencies?.vite;
    if (!expected) {
        return { checked: false, reason: '@angular/build no longer declares a vite dependency' };
    }
    if (!EXACT_VERSION.test(expected)) {
        // Angular pins vite exactly today. If that ever becomes a range, comparing strings would
        // produce false alarms, so leave the check to whoever adapts it to range semantics.
        return { checked: false, reason: `@angular/build declares a vite range ("${expected}") rather than an exact version` };
    }

    let actual;
    try {
        actual = createRequire(manifestPath)('vite/package.json').version;
    } catch {
        // Either a broken install or a future vite that stops exporting ./package.json. Both are
        // reported far more usefully by the build tool itself than by a bare module-resolution error.
        return { checked: false, reason: 'vite could not be resolved from @angular/build' };
    }

    // GHSA-vfpm-58rq-9qcg and the other Vite 8.3.0 advisories are fixed in this same-minor patch.
    // Keep the exception tied to the reviewed builder, declared pin, and installed version.
    const reviewedSecurityPatch = angularBuild.version === '22.2.3' && expected === '8.3.0' && actual === '8.3.3';
    if (actual !== expected && !reviewedSecurityPatch) {
        throw new Error(
            `vite ${actual} is installed for @angular/build, which depends on vite ${expected}.\n` +
                `This combination has not been reviewed. Incompatible Vite overrides can disable the Angular linker\n` +
                `during dependency pre-bundling, so "pnpm start" renders a blank page even if the production build passes.\n` +
                `Fix: remove the vite override from pnpm-workspace.yaml (or set it to '${expected}'), then run "pnpm install".`,
        );
    }

    return { checked: true };
}

/**
 * Runs the check and reports the outcome on the console, exiting non-zero on a mismatch. Shared by
 * this script's standalone entry point and by `prebuild.mjs`, so both behave identically.
 *
 * @param {object} [options] Forwarded to {@link checkViteOverride}.
 */
export function runViteOverrideCheck(options) {
    let result;
    try {
        result = checkViteOverride(options);
    } catch (error) {
        // The message is self-contained and actionable; a stack trace would only bury it.
        console.error(error.message);
        process.exit(1);
        return;
    }
    console.log(result.checked ? 'vite matches the required or reviewed security-patched version for @angular/build.' : `Skipped vite override check: ${result.reason}.`);
}

// Also runnable standalone: `pnpm run check:vite-override`.
if (import.meta.main) {
    runViteOverrideCheck();
}
