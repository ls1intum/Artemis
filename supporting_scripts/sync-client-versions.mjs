#!/usr/bin/env node

/**
 * Syncs client dependency versions from the root .env file into package.json files.
 *
 * The .env file is the single source of truth for version groups that span multiple
 * npm packages (e.g. Angular, Angular ESLint, TypeScript ESLint, Playwright).
 *
 * Usage:
 *   node supporting_scripts/sync-client-versions.mjs          # sync .env -> package.json
 *   node supporting_scripts/sync-client-versions.mjs --check  # verify versions match (CI)
 *   node supporting_scripts/sync-client-versions.mjs --reverse # sync package.json -> .env
 */

import { readFileSync, writeFileSync, existsSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(__dirname, '..');

const ENV_FILE = resolve(ROOT, '.env');
const ROOT_PKG = resolve(ROOT, 'package.json');
const PLAYWRIGHT_PKG = resolve(ROOT, 'src/test/playwright/package.json');

// ── .env parsing ──────────────────────────────────────────────────────────────

function parseEnv(path) {
    const vars = {};
    for (const line of readFileSync(path, 'utf8').split('\n')) {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#') || !trimmed.includes('=')) continue;
        const [key, ...rest] = trimmed.split('=');
        vars[key.trim()] = rest.join('=').trim();
    }
    return vars;
}

function writeEnv(path, vars) {
    const lines = readFileSync(path, 'utf8').split('\n');
    const updated = lines.map((line) => {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#') || !trimmed.includes('=')) return line;
        const [key] = trimmed.split('=', 1);
        if (key.trim() in vars) {
            return `${key.trim()}=${vars[key.trim()]}`;
        }
        return line;
    });
    writeFileSync(path, updated.join('\n'));
}

// ── Package.json helpers ──────────────────────────────────────────────────────

function readPkg(path) {
    return JSON.parse(readFileSync(path, 'utf8'));
}

function writePkg(path, pkg) {
    writeFileSync(path, JSON.stringify(pkg, null, 4) + '\n');
}

// ── Version mapping: which .env variable controls which packages ─────────────

/** Given a package name and current version, return the .env key that controls it. */
function envKeyForPackage(name) {
    // Angular core packages (@angular/*, @angular-devkit/build-angular)
    // Note: @angular-builders/* are third-party packages with independent versioning
    if (name.startsWith('@angular/') || name === '@angular-devkit/build-angular') {
        return 'ANGULAR_VERSION';
    }
    // Angular ESLint (@angular-eslint/*, angular-eslint)
    if (name.startsWith('@angular-eslint/') || name === 'angular-eslint') {
        return 'ANGULAR_ESLINT_VERSION';
    }
    // TypeScript ESLint (@typescript-eslint/*, typescript-eslint)
    if (name.startsWith('@typescript-eslint/') || name === 'typescript-eslint') {
        return 'TYPESCRIPT_ESLINT_VERSION';
    }
    // Playwright
    if (name === '@playwright/test') {
        return 'PLAYWRIGHT_VERSION';
    }
    return null;
}

/**
 * Transform the .env value for use in package.json.
 * E.g. PLAYWRIGHT_VERSION "v1.58.2" → "1.58.2" (strip 'v' prefix).
 */
function envValueToNpmVersion(envKey, envValue) {
    if (envKey === 'PLAYWRIGHT_VERSION') {
        return envValue.replace(/^v/, '');
    }
    return envValue;
}

/**
 * Transform a package.json version back to .env format.
 * E.g. Playwright "1.58.2" → "v1.58.2" (add 'v' prefix for Docker tag).
 */
function npmVersionToEnvValue(envKey, npmVersion) {
    if (envKey === 'PLAYWRIGHT_VERSION') {
        return npmVersion.startsWith('v') ? npmVersion : `v${npmVersion}`;
    }
    return npmVersion;
}

/**
 * Derive the override version pattern from a concrete version.
 * "21.1.3" → "^21.1.0" (caret on major.minor.0)
 */
function toOverridePattern(version) {
    const parts = version.split('.');
    if (parts.length >= 3) {
        return `^${parts[0]}.${parts[1]}.0`;
    }
    return `^${version}`;
}

// ── Sync logic ────────────────────────────────────────────────────────────────

function syncForward(env, checkOnly) {
    let changes = 0;
    let mismatches = [];

    // --- Root package.json ---
    const pkg = readPkg(ROOT_PKG);

    for (const section of ['dependencies', 'devDependencies']) {
        if (!pkg[section]) continue;
        for (const [name, currentVersion] of Object.entries(pkg[section])) {
            const envKey = envKeyForPackage(name);
            if (!envKey || !(envKey in env)) continue;
            const targetVersion = envValueToNpmVersion(envKey, env[envKey]);
            if (currentVersion !== targetVersion) {
                mismatches.push({ file: 'package.json', section, name, current: currentVersion, target: targetVersion, envKey });
                pkg[section][name] = targetVersion;
                changes++;
            }
        }
    }

    // Update Angular overrides (use caret pattern ^MAJOR.MINOR.0)
    if (pkg.overrides && env.ANGULAR_VERSION) {
        const overridePattern = toOverridePattern(env.ANGULAR_VERSION);
        for (const [libName, libOverrides] of Object.entries(pkg.overrides)) {
            if (typeof libOverrides !== 'object') continue;
            for (const [depName, currentPattern] of Object.entries(libOverrides)) {
                if (envKeyForPackage(depName) === 'ANGULAR_VERSION' && currentPattern !== overridePattern) {
                    mismatches.push({ file: 'package.json', section: `overrides.${libName}`, name: depName, current: currentPattern, target: overridePattern, envKey: 'ANGULAR_VERSION' });
                    pkg.overrides[libName][depName] = overridePattern;
                    changes++;
                }
            }
        }
    }

    // --- Playwright package.json ---
    if (existsSync(PLAYWRIGHT_PKG) && env.PLAYWRIGHT_VERSION) {
        const playwrightPkg = readPkg(PLAYWRIGHT_PKG);
        const targetVersion = envValueToNpmVersion('PLAYWRIGHT_VERSION', env.PLAYWRIGHT_VERSION);
        for (const section of ['dependencies', 'devDependencies']) {
            if (!playwrightPkg[section]) continue;
            for (const [name, currentVersion] of Object.entries(playwrightPkg[section])) {
                if (name === '@playwright/test' && currentVersion !== targetVersion) {
                    mismatches.push({ file: 'src/test/playwright/package.json', section, name, current: currentVersion, target: targetVersion, envKey: 'PLAYWRIGHT_VERSION' });
                    playwrightPkg[section][name] = targetVersion;
                    changes++;
                }
            }
        }
        if (!checkOnly && changes > 0) {
            writePkg(PLAYWRIGHT_PKG, playwrightPkg);
        }
    }

    // --- Report ---
    if (mismatches.length === 0) {
        console.log('All client versions are in sync with .env');
        return 0;
    }

    console.log(`Found ${mismatches.length} version mismatch(es):\n`);
    for (const m of mismatches) {
        console.log(`  ${m.file} > ${m.section} > ${m.name}`);
        console.log(`    current: ${m.current}  →  target: ${m.target}  (from ${m.envKey})`);
    }

    if (checkOnly) {
        console.log('\nRun `node supporting_scripts/sync-client-versions.mjs` to fix.');
        return 1;
    }

    writePkg(ROOT_PKG, pkg);
    console.log(`\nUpdated ${changes} version(s). Run \`npm install\` to apply.`);
    return 0;
}

function syncReverse(env) {
    const pkg = readPkg(ROOT_PKG);
    const updates = {};

    // Extract canonical version for each .env key from package.json
    // Use the first matching package as the source of truth
    const canonicalSources = {
        ANGULAR_VERSION: '@angular/core',
        ANGULAR_ESLINT_VERSION: '@angular-eslint/eslint-plugin',
        TYPESCRIPT_ESLINT_VERSION: '@typescript-eslint/eslint-plugin',
    };

    for (const [envKey, pkgName] of Object.entries(canonicalSources)) {
        const version = pkg.dependencies?.[pkgName] || pkg.devDependencies?.[pkgName];
        if (version) {
            const envValue = npmVersionToEnvValue(envKey, version);
            if (env[envKey] !== envValue) {
                updates[envKey] = envValue;
                console.log(`  ${envKey}: ${env[envKey] || '(not set)'}  →  ${envValue}  (from ${pkgName})`);
            }
        }
    }

    // Playwright: read from playwright package.json
    if (existsSync(PLAYWRIGHT_PKG)) {
        const playwrightPkg = readPkg(PLAYWRIGHT_PKG);
        const version = playwrightPkg.devDependencies?.['@playwright/test'];
        if (version) {
            const envValue = npmVersionToEnvValue('PLAYWRIGHT_VERSION', version);
            if (env.PLAYWRIGHT_VERSION !== envValue) {
                updates.PLAYWRIGHT_VERSION = envValue;
                console.log(`  PLAYWRIGHT_VERSION: ${env.PLAYWRIGHT_VERSION || '(not set)'}  →  ${envValue}  (from @playwright/test)`);
            }
        }
    }

    if (Object.keys(updates).length === 0) {
        console.log('.env is already in sync with package.json');
        return 0;
    }

    writeEnv(ENV_FILE, { ...env, ...updates });
    console.log(`\nUpdated .env with ${Object.keys(updates).length} version(s).`);
    return 0;
}

// ── Main ──────────────────────────────────────────────────────────────────────

const args = process.argv.slice(2);
const checkOnly = args.includes('--check');
const reverse = args.includes('--reverse');

if (!existsSync(ENV_FILE)) {
    console.error(`Error: ${ENV_FILE} not found`);
    process.exit(1);
}

const env = parseEnv(ENV_FILE);

if (reverse) {
    console.log('Syncing package.json → .env (reverse)\n');
    process.exit(syncReverse(env));
} else {
    console.log(`Syncing .env → package.json${checkOnly ? ' (check only)' : ''}\n`);
    process.exit(syncForward(env, checkOnly));
}
