/**
 * Guards the `vite` override in `pnpm-workspace.yaml` against drifting away from the version
 * `@angular/build` actually depends on.
 *
 * Why this needs a guard: the Angular dev server configures its dependency pre-bundling through
 * Vite-version-specific options. Angular 22.1 moved that configuration from esbuild
 * (`optimizeDeps.esbuildOptions`) to rolldown (`optimizeDeps.rolldownOptions`), which only Vite 8
 * understands. When an override holds Vite on an older major, Vite silently ignores the unknown
 * option block — and with it the `angular-vite-optimize-deps` plugin that runs the Angular linker
 * over pre-bundled dependencies. Partially compiled Angular libraries then reach the browser with
 * their `ɵɵngDeclare*` calls intact and bootstrap dies with "The injectable '_PlatformLocation'
 * needs to be compiled using the JIT compiler, but '@angular/compiler' is not available."
 *
 * Nothing about that failure points at the override: the production build is unaffected (it never
 * goes through Vite's dependency optimizer), so only `pnpm start` and the E2E suites that run
 * against it break. This check turns that silent breakage into an actionable error at build time.
 */
import { createRequire } from 'node:module';

const EXACT_VERSION = /^\d+\.\d+\.\d+(?:-[0-9A-Za-z-.]+)?$/;

/**
 * Verifies that the `vite` resolved for `@angular/build` is the exact version `@angular/build`
 * asks for. Throws with a remediation hint when an override downgrades or upgrades it.
 */
export function checkViteOverride() {
    const require = createRequire(import.meta.url);

    let manifestPath;
    try {
        manifestPath = require.resolve('@angular/build/package.json');
    } catch {
        // A broken/incomplete install fails loudly a moment later in the build itself.
        console.warn('Skipping vite override check: @angular/build is not installed.');
        return;
    }

    const expected = require(manifestPath).dependencies?.vite;
    if (!expected) {
        console.warn('Skipping vite override check: @angular/build no longer declares a vite dependency.');
        return;
    }
    if (!EXACT_VERSION.test(expected)) {
        // Angular pins vite exactly today. If that ever becomes a range, comparing strings would
        // produce false alarms, so leave the check to whoever adapts it to range semantics.
        console.warn(`Skipping vite override check: @angular/build declares a vite range ("${expected}") rather than an exact version.`);
        return;
    }

    const actual = createRequire(manifestPath)('vite/package.json').version;
    if (actual === expected) {
        return;
    }

    throw new Error(
        `vite ${actual} is installed for @angular/build, which depends on vite ${expected}.\n` +
            `The Angular dev server passes its dependency pre-bundling options (including the Angular linker plugin)\n` +
            `in a format tied to that vite version. On a mismatch vite ignores them and the pre-bundled Angular\n` +
            `libraries are served unlinked, so "pnpm start" renders a blank page.\n` +
            `Fix: set overrides.vite to '${expected}' in pnpm-workspace.yaml, then run "pnpm install".`,
    );
}

// Also runnable standalone: `pnpm run check:vite-override`.
if (process.argv[1] && import.meta.url === new URL(`file://${process.argv[1]}`).href) {
    try {
        checkViteOverride();
        console.log('vite override matches the version required by @angular/build.');
    } catch (error) {
        console.error(error.message);
        process.exit(1);
    }
}
