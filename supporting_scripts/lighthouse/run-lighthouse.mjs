#!/usr/bin/env node
/**
 * Runs Google Lighthouse against a production build of the Artemis landing page and
 * writes the HTML + JSON reports into build/reports/lighthouse/. Invoke via:
 *
 *   npm run lighthouse                # mobile preset (matches PageSpeed Insights)
 *   npm run lighthouse -- --desktop   # desktop preset
 *   npm run lighthouse -- --skip-build  # reuse an existing webapp:prod build
 *
 * The script boots a tiny SPA-aware static server over the built assets, waits for it
 * to respond, runs Lighthouse, and kills the server regardless of success/failure.
 */

import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, statSync } from 'node:fs';
import { extname, join, normalize, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const repoRoot = resolve(__dirname, '..', '..');
const staticRoot = join(repoRoot, 'build', 'resources', 'main', 'static');
const reportsDir = join(repoRoot, 'build', 'reports', 'lighthouse');

const args = new Set(process.argv.slice(2));
const desktop = args.has('--desktop');
const skipBuild = args.has('--skip-build');
const port = Number(process.env.LIGHTHOUSE_PORT || 4173);

const MIME_TYPES = {
    '.html': 'text/html; charset=utf-8',
    '.js': 'application/javascript; charset=utf-8',
    '.mjs': 'application/javascript; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.webp': 'image/webp',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.svg': 'image/svg+xml',
    '.woff': 'font/woff',
    '.woff2': 'font/woff2',
    '.webm': 'video/webm',
    '.mp4': 'video/mp4',
    '.vtt': 'text/vtt; charset=utf-8',
    '.map': 'application/json; charset=utf-8',
    '.txt': 'text/plain; charset=utf-8',
    '.ico': 'image/x-icon',
};

function runCommand(cmd, cmdArgs, opts = {}) {
    return new Promise((resolvePromise, rejectPromise) => {
        const child = spawn(cmd, cmdArgs, { stdio: 'inherit', cwd: repoRoot, ...opts });
        child.once('error', rejectPromise);
        child.once('exit', (code) => (code === 0 ? resolvePromise() : rejectPromise(new Error(`${cmd} exited with code ${code}`))));
    });
}

/* Minimal stubs for the two API calls Angular makes at bootstrap. Without these, the SPA
   fallback would return index.html for /management/info, breaking JSON parsing and crashing
   the app before the landing page can render. */
const MOCK_PROFILE_INFO = {
    activeProfiles: ['prod'],
    activeModuleFeatures: [],
    allowedLdapUsernamePattern: '.*',
    allowedCourseRegistrationUsernamePattern: '.*',
    allowedEmailPattern: '.*',
    allowedEmailPatternReadable: 'any',
    accountName: 'Artemis',
    buildTimeoutDefault: 120,
    buildTimeoutMin: 10,
    buildTimeoutMax: 240,
    contact: 'artemis@example.org',
    continuousIntegrationName: 'none',
    defaultContainerCpuCount: 1,
    defaultContainerMemoryLimitInMB: 512,
    defaultContainerMemorySwapLimitInMB: 512,
    externalCredentialProvider: '',
    externalPasswordResetLinkMap: { en: '', de: '' },
    features: {},
    git: {
        branch: 'main',
        commit: {
            id: 'local',
            time: new Date().toISOString(),
            user: { name: 'local', email: 'local@example.org' },
        },
    },
    java: { version: '25' },
    build: {
        artifact: 'artemis',
        name: 'artemis',
        version: 'local',
        time: new Date().toISOString(),
        group: 'de.tum.cit.aet',
    },
    compatibleVersions: { ios: { min: '0', recommended: '0' }, android: { min: '0', recommended: '0' } },
    operatorName: 'Local',
    operatorAdminName: 'Local Admin',
    programmingLanguageFeatures: [],
    registrationEnabled: false,
    repositoryAuthenticationMechanisms: ['password'],
    sentry: { dsn: '' },
    sshCloneURLTemplate: '',
    studentExamStoreSessionData: false,
    theiaPortalURL: '',
    useExternal: false,
    versionControlName: 'none',
    versionControlUrl: '',
    inProduction: true,
    testServer: false,
    needsToAcceptTerms: false,
};

function handleApiMock(urlPath, res) {
    // /management/info — drives the profile service; must return valid ProfileInfo JSON
    if (urlPath === '/management/info') {
        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify(MOCK_PROFILE_INFO));
        return true;
    }
    // Unauthenticated probe — a 401 tells the account service there's no logged-in user,
    // which is exactly the state the landing page is designed for.
    if (urlPath === '/api/core/public/account') {
        res.writeHead(401, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end('{}');
        return true;
    }
    // Anything else under /api or /management: 404 cleanly so the app can handle it without
    // misinterpreting HTML as JSON.
    if (urlPath.startsWith('/api/') || urlPath.startsWith('/management/') || urlPath.startsWith('/websocket')) {
        res.writeHead(404, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end('{"status":404,"message":"Not mocked by lighthouse runner"}');
        return true;
    }
    return false;
}

const resolvedStaticRoot = resolve(staticRoot);
const indexHtmlPath = join(resolvedStaticRoot, 'index.html');

/**
 * Resolves a request path safely against the static root. Returns the canonical file path
 * if it lives inside the root, or `null` if the request would escape the jail, contains
 * illegal bytes, or matches a directory (SPA fallback handled by the caller).
 */
function resolveSafeFilePath(urlPath) {
    if (urlPath.includes('\0')) {
        return null;
    }
    const normalized = normalize(urlPath);
    // Reject any residual parent-directory segment after normalization — this happens
    // when the URL traverses above the static root (e.g. `/../../etc/passwd`).
    if (normalized.startsWith('..') || normalized.includes(`${sep}..${sep}`)) {
        return null;
    }
    const candidate = resolve(resolvedStaticRoot, '.' + (normalized.startsWith('/') ? normalized : '/' + normalized));
    if (candidate !== resolvedStaticRoot && !candidate.startsWith(resolvedStaticRoot + sep)) {
        return null;
    }
    return candidate;
}

function startStaticServer() {
    return new Promise((resolvePromise, rejectPromise) => {
        const server = createServer((req, res) => {
            const rawPath = (req.url || '/').split('?')[0];
            let urlPath;
            try {
                urlPath = decodeURIComponent(rawPath);
            } catch {
                // Lighthouse sometimes synthesizes URLs with malformed percent-encoding during
                // audits; keep the server alive so the run completes.
                res.writeHead(400).end();
                return;
            }

            if (handleApiMock(urlPath, res)) {
                return;
            }

            const safePath = resolveSafeFilePath(urlPath);
            if (safePath === null) {
                res.writeHead(403).end();
                return;
            }

            // SPA fallback: serve index.html for any path without a file extension
            const hasExtension = extname(urlPath) !== '';
            const filePath = hasExtension && existsSync(safePath) && statSync(safePath).isFile() ? safePath : indexHtmlPath;

            const ext = extname(filePath).toLowerCase();
            // index.html must not be cached so Lighthouse sees the current build; fingerprinted
            // assets get a long cache lifetime to mirror how a real CDN would serve them.
            const isHtml = ext === '.html';
            res.writeHead(200, {
                'Content-Type': MIME_TYPES[ext] || 'application/octet-stream',
                'Cache-Control': isHtml ? 'no-cache, no-store, must-revalidate' : 'public, max-age=31536000, immutable',
            });
            res.end(readFileSync(filePath));
        });
        server.on('error', rejectPromise);
        server.listen(port, '127.0.0.1', () => resolvePromise(server));
    });
}

async function waitForServer(url, timeoutMs = 10_000) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
        try {
            const response = await fetch(url);
            if (response.ok) {
                return;
            }
        } catch {
            // server not ready yet
        }
        await new Promise((r) => setTimeout(r, 200));
    }
    throw new Error(`Static server did not become ready at ${url} within ${timeoutMs} ms`);
}

async function main() {
    if (!skipBuild) {
        if (!existsSync(join(staticRoot, 'index.html'))) {
            console.log('> No production build found — running `npm run webapp:prod` …');
        } else {
            console.log('> Refreshing production build via `npm run webapp:prod` (use --skip-build to reuse an existing build)');
        }
        await runCommand('npm', ['run', 'webapp:prod']);
    }

    if (!existsSync(join(staticRoot, 'index.html'))) {
        throw new Error(`Expected built index.html at ${staticRoot}. Run \`npm run webapp:prod\` first, or drop --skip-build.`);
    }

    mkdirSync(reportsDir, { recursive: true });

    const server = await startStaticServer();
    const targetUrl = `http://127.0.0.1:${port}/`;
    console.log(`> Static server listening on ${targetUrl}`);

    try {
        await waitForServer(targetUrl);

        const lighthouseBin = join(repoRoot, 'node_modules', '.bin', 'lighthouse');
        if (!existsSync(lighthouseBin)) {
            throw new Error(`lighthouse is not installed. Run: npm install --save-dev lighthouse`);
        }

        const lighthouseArgs = [
            targetUrl,
            `--chrome-flags=--headless=new --no-sandbox --disable-gpu`,
            '--only-categories=performance,accessibility,best-practices,seo',
            '--output=html,json',
            `--output-path=${join(reportsDir, 'landing')}`,
            '--quiet',
        ];
        if (desktop) {
            lighthouseArgs.push('--preset=desktop');
        } else {
            lighthouseArgs.push('--form-factor=mobile');
            // `devtools` uses real Chromium throttling — on localhost the Lantern simulator
            // sometimes fails to identify LCP because the trace spans only milliseconds, so we
            // prefer real throttling which reliably produces all metrics.
            lighthouseArgs.push('--throttling-method=devtools');
        }

        console.log(`> Running Lighthouse (${desktop ? 'desktop' : 'mobile'}) against ${targetUrl}`);
        await runCommand(lighthouseBin, lighthouseArgs);

        console.log(`> Report written to ${join(reportsDir, 'landing.report.html')}`);
    } finally {
        server.close();
    }
}

main().catch((err) => {
    console.error(err);
    process.exit(1);
});
