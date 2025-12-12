#!/usr/bin/env node

/**
 * Local PR Coverage Report Generator
 *
 * This script generates a code coverage report for changed files in a PR by:
 * 1. Detecting changed files vs. origin/develop (or specified base branch)
 * 2. Identifying affected modules from the changed files
 * 3. Running only the relevant module tests locally
 * 4. Generating a coverage report table for the changed files
 *
 * Usage:
 *   node local-pr-coverage.mjs [options]
 *
 * Options:
 *   --base-branch <branch>       Base branch to compare against (default: origin/develop)
 *   --client-modules <modules>   Comma-separated list of client modules to test (e.g., core,shared)
 *   --server-modules <modules>   Comma-separated list of server modules to test (e.g., core,exam)
 *   --skip-tests                 Skip running tests, use existing coverage data
 *   --client-only                Only run client tests
 *   --server-only                Only run server tests
 *   --print                      Print results to console (default: copy to clipboard)
 *   --verbose                    Enable verbose logging
 *   --help                       Show help
 */

import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PROJECT_ROOT = path.resolve(__dirname, '../../..');

// Configuration
const CLIENT_SRC_PREFIX = 'src/main/webapp/app/';
const SERVER_SRC_PREFIX = 'src/main/java/de/tum/cit/aet/artemis/';
const CLIENT_COVERAGE_SUMMARY = path.join(PROJECT_ROOT, 'build/test-results/coverage-summary.json');
const SERVER_COVERAGE_DIR = path.join(PROJECT_ROOT, 'build/reports/jacoco');

// Module name validation pattern - only allow safe characters (alphanumeric, dash, underscore)
const SAFE_MODULE_NAME_PATTERN = /^[a-zA-Z0-9_-]+$/;

/**
 * Validate and filter module names to prevent shell injection
 */
function validateModuleNames(modules, optionName) {
    const validModules = [];
    for (const module of modules) {
        if (SAFE_MODULE_NAME_PATTERN.test(module)) {
            validModules.push(module);
        } else {
            console.error(`Error: Invalid module name "${module}" in ${optionName}. Module names can only contain letters, numbers, dashes, and underscores.`);
            process.exit(1);
        }
    }
    return validModules;
}

// Parse command line arguments
function parseArgs() {
    const args = process.argv.slice(2);
    const options = {
        baseBranch: 'origin/develop',
        clientModules: [], // Explicitly specified client modules
        serverModules: [], // Explicitly specified server modules
        skipTests: false,
        clientOnly: false,
        serverOnly: false,
        print: false,
        verbose: false,
        help: false,
    };

    for (let i = 0; i < args.length; i++) {
        switch (args[i]) {
            case '--base-branch':
                if (i + 1 >= args.length) {
                    console.error('Error: --base-branch requires a value');
                    process.exit(1);
                }
                options.baseBranch = args[++i];
                break;
            case '--client-modules':
                if (i + 1 >= args.length) {
                    console.error('Error: --client-modules requires a comma-separated list of modules');
                    process.exit(1);
                }
                options.clientModules = validateModuleNames(
                    args[++i].split(',').map((m) => m.trim()).filter(Boolean),
                    '--client-modules'
                );
                break;
            case '--server-modules':
                if (i + 1 >= args.length) {
                    console.error('Error: --server-modules requires a comma-separated list of modules');
                    process.exit(1);
                }
                options.serverModules = validateModuleNames(
                    args[++i].split(',').map((m) => m.trim()).filter(Boolean),
                    '--server-modules'
                );
                break;
            case '--skip-tests':
                options.skipTests = true;
                break;
            case '--client-only':
                options.clientOnly = true;
                break;
            case '--server-only':
                options.serverOnly = true;
                break;
            case '--print':
                options.print = true;
                break;
            case '--verbose':
                options.verbose = true;
                break;
            case '--help':
                options.help = true;
                break;
        }
    }

    return options;
}

function showHelp() {
    console.log(`
Local PR Coverage Report Generator

Generates a code coverage report for changed files by running tests locally
for only the affected modules. This is faster and more reliable than waiting
for CI builds.

Usage:
  node local-pr-coverage.mjs [options]
  npm run coverage:pr [-- options]

Options:
  --base-branch <branch>       Base branch to compare against (default: origin/develop)
  --client-modules <modules>   Comma-separated list of client modules to test (e.g., core,shared)
  --server-modules <modules>   Comma-separated list of server modules to test (e.g., core,exam)
  --skip-tests                 Skip running tests, use existing coverage data
  --client-only                Only run client tests (auto-detected or specified modules)
  --server-only                Only run server tests (auto-detected or specified modules)
  --print                      Print results to console (default: copy to clipboard)
  --verbose                    Enable verbose logging
  --help                       Show this help

Module Selection:
  By default, modules to test are auto-detected from changed files.
  Use --client-modules or --server-modules to override which modules to test.
  The coverage report always shows only the files that changed vs. the base branch.

Available Modules:
  Client: core, shared, exam, exercise, programming, quiz, communication, etc.
  Server: core, exam, exercise, programming, quiz, communication, atlas, etc.

Examples:
  # Auto-detect modules from changed files
  node local-pr-coverage.mjs

  # Test specific client modules only
  npm run coverage:pr -- --client-modules core,shared --client-only

  # Test specific server modules only
  npm run coverage:pr -- --server-modules core,exam --server-only

  # Mix: auto-detect client, specify server modules
  npm run coverage:pr -- --server-modules core

  # Skip tests and use existing coverage data
  npm run coverage:pr -- --skip-tests --client-modules core
`);
}

function log(message, options) {
    if (options.verbose) {
        console.log(`[DEBUG] ${message}`);
    }
}

function info(message) {
    console.log(`ℹ️  ${message}`);
}

function success(message) {
    console.log(`✅ ${message}`);
}

function warn(message) {
    console.warn(`⚠️  ${message}`);
}

function error(message) {
    console.error(`❌ ${message}`);
}

/**
 * Get list of changed files compared to base branch
 */
function getChangedFiles(baseBranch, options) {
    try {
        // Fetch the base branch to ensure we have the latest
        try {
            execSync(`git fetch origin ${baseBranch.replace('origin/', '')}`, {
                cwd: PROJECT_ROOT,
                stdio: 'pipe',
            });
        } catch {
            log(`Could not fetch ${baseBranch}, using local version`, options);
        }

        // Get the merge base
        const mergeBase = execSync(`git merge-base HEAD ${baseBranch}`, {
            cwd: PROJECT_ROOT,
            encoding: 'utf-8',
        }).trim();

        log(`Merge base: ${mergeBase}`, options);

        // Get changed files
        const diffOutput = execSync(`git diff --name-status ${mergeBase}...HEAD`, {
            cwd: PROJECT_ROOT,
            encoding: 'utf-8',
        });

        const changes = {};
        for (const line of diffOutput.split('\n').filter(Boolean)) {
            const [status, ...fileParts] = line.split('\t');
            // For renames (R), git outputs "R100\told_path\tnew_path" - take only the new path
            const filePath = status.charAt(0) === 'R' ? fileParts[fileParts.length - 1] : fileParts.join('\t');
            if (filePath) {
                changes[filePath] =
                    {
                        A: 'added',
                        D: 'deleted',
                        M: 'modified',
                        R: 'renamed',
                    }[status.charAt(0)] || 'unknown';
            }
        }

        return changes;
    } catch (err) {
        error(`Failed to get changed files: ${err.message}`);
        process.exit(1);
    }
}

/**
 * Filter and categorize changed files into client and server
 */
function categorizeChangedFiles(changedFiles, options) {
    const clientFiles = {};
    const serverFiles = {};
    const clientModules = new Set();
    const serverModules = new Set();

    // Files to exclude from coverage reporting (cannot be properly tested)
    const excludedClientPatterns = ['.module.ts', '.spec.ts', '.routes.ts', '.route.ts'];
    const excludedClientFiles = ['app.component.ts', 'app.config.ts', 'app.constants.ts', 'app.main.ts', 'app.routes.ts', 'polyfills.ts', 'primeng-artemis-theme.ts'];

    for (const [filePath, changeType] of Object.entries(changedFiles)) {
        // Client files
        if (filePath.startsWith(CLIENT_SRC_PREFIX) && filePath.endsWith('.ts')) {
            // Skip excluded patterns
            if (excludedClientPatterns.some((pattern) => filePath.endsWith(pattern))) {
                log(`Skipping (excluded pattern): ${filePath}`, options);
                continue;
            }

            const relativePath = filePath.substring(CLIENT_SRC_PREFIX.length);

            // Skip excluded files
            if (excludedClientFiles.includes(relativePath)) {
                log(`Skipping (excluded file): ${filePath}`, options);
                continue;
            }

            clientFiles[relativePath] = changeType;

            // Extract module name (first directory after app/)
            const moduleName = relativePath.split('/')[0];
            if (moduleName && !relativePath.includes('/')) {
                // File is directly in app/, not in a module
                log(`Client file (root): ${relativePath}`, options);
            } else if (moduleName) {
                clientModules.add(moduleName);
                log(`Client file: ${relativePath} (module: ${moduleName})`, options);
            }
        }
        // Server files
        else if (filePath.startsWith(SERVER_SRC_PREFIX) && filePath.endsWith('.java')) {
            const relativePath = filePath.substring('src/main/java/'.length);
            serverFiles[relativePath] = changeType;

            // Extract module name
            const afterArtemis = filePath.substring(SERVER_SRC_PREFIX.length);
            const moduleName = afterArtemis.split('/')[0];
            if (moduleName) {
                serverModules.add(moduleName);
            }

            log(`Server file: ${relativePath} (module: ${moduleName})`, options);
        } else {
            log(`Skipping: ${filePath}`, options);
        }
    }

    return {
        clientFiles,
        serverFiles,
        clientModules: Array.from(clientModules),
        serverModules: Array.from(serverModules),
    };
}

/**
 * Run client tests for specific modules
 */
async function runClientTests(modules, options) {
    if (modules.length === 0) {
        info('No client modules to test');
        return true;
    }

    info(`Running client tests for modules: ${modules.join(', ')}`);

    // Build test pattern to match files in the specified modules
    // Jest needs absolute paths with --test-path-pattern
    const testPattern = modules.map((m) => `^${PROJECT_ROOT}/src/main/webapp/app/${m}/`).join('|');

    // Run prebuild first, then ng test with the pattern
    const command = `npm run prebuild && npx ng test --coverage --log-heap-usage -w=4 --test-path-pattern="${testPattern}"`;

    log(`Running: ${command}`, options);

    try {
        execSync(command, {
            cwd: PROJECT_ROOT,
            stdio: options.verbose ? 'inherit' : 'pipe',
            encoding: 'utf-8',
        });
        success('Client tests completed');
        return true;
    } catch (err) {
        warn(`Client tests exited with code ${err.status || 1}`);
        if (!options.verbose && err.stdout) {
            console.log(err.stdout);
        }
        if (!options.verbose && err.stderr) {
            console.error(err.stderr);
        }
        return false;
    }
}

/**
 * Run server tests for specific modules
 */
async function runServerTests(modules, options) {
    if (modules.length === 0) {
        info('No server modules to test');
        return true;
    }

    info(`Running server tests for modules: ${modules.join(', ')}`);

    const modulesArg = modules.join(',');
    const command = `./gradlew test -DincludeModules=${modulesArg} jacocoTestReport -x webapp`;

    log(`Running: ${command}`, options);

    try {
        execSync(command, {
            cwd: PROJECT_ROOT,
            stdio: options.verbose ? 'inherit' : 'pipe',
            encoding: 'utf-8',
        });
        success('Server tests completed');
        return true;
    } catch (err) {
        warn(`Server tests exited with code ${err.status || 1}`);
        if (!options.verbose && err.stdout) {
            console.log(err.stdout);
        }
        if (!options.verbose && err.stderr) {
            console.error(err.stderr);
        }
        return false;
    }
}

/**
 * Get client coverage for a specific file from coverage-summary.json
 */
function getClientFileCoverage(filePath, coverageSummary) {
    // The coverage summary uses full paths from src/main/webapp/
    const fullPath = `src/main/webapp/app/${filePath}`;

    for (const [coveragePath, metrics] of Object.entries(coverageSummary)) {
        if (coveragePath.endsWith(fullPath) || coveragePath.includes(fullPath)) {
            if (metrics.lines && typeof metrics.lines.pct === 'number') {
                return `${metrics.lines.pct.toFixed(2)}%`;
            }
        }
    }

    return null;
}

/**
 * Parse JaCoCo XML report to get coverage for a specific class
 */
function getServerFileCoverage(filePath, moduleName) {
    // Convert file path to package/class format
    const withoutJava = filePath.replace('.java', '');
    const parts = withoutJava.split('/');
    const className = parts[parts.length - 1];
    const packagePath = parts.slice(0, -1).join('.');

    // Try module-specific report first, then aggregated
    const reportPaths = [path.join(SERVER_COVERAGE_DIR, moduleName, 'jacocoTestReport.xml'), path.join(SERVER_COVERAGE_DIR, 'aggregated', 'jacocoTestReport.xml')];

    for (const reportPath of reportPaths) {
        if (!fs.existsSync(reportPath)) {
            continue;
        }

        try {
            const xmlContent = fs.readFileSync(reportPath, 'utf-8');

            // Search by sourcefilename to find the class in JaCoCo XML
            const sourceFileRegex = new RegExp(`<class[^>]*sourcefilename="${className}\\.java"[^>]*>([\\s\\S]*?)</class>`, 'gi');

            let match = xmlContent.match(sourceFileRegex);
            if (match) {
                // Find the class-level LINE counter (the LAST one in the class block, not method-level)
                for (const classMatch of match) {
                    // Get ALL LINE counters in this class block
                    const lineCounterRegex = /<counter[^>]*type="LINE"[^>]*\/?>/gi;
                    const allLineCounters = classMatch.match(lineCounterRegex);

                    if (allLineCounters && allLineCounters.length > 0) {
                        // The class-level counter is the LAST one (after all method counters)
                        const classLevelCounter = allLineCounters[allLineCounters.length - 1];

                        // Extract missed and covered values
                        const missedMatch = classLevelCounter.match(/missed="(\d+)"/);
                        const coveredMatch = classLevelCounter.match(/covered="(\d+)"/);

                        if (missedMatch && coveredMatch) {
                            const missed = parseInt(missedMatch[1], 10);
                            const covered = parseInt(coveredMatch[1], 10);
                            const total = missed + covered;
                            if (total > 0) {
                                const percentage = (covered / total) * 100;
                                return `${percentage.toFixed(2)}%`;
                            }
                        }
                    }
                }
            }
        } catch (err) {
            // Continue to next report
        }
    }

    return null;
}

/**
 * Get line count of a source file (excluding empty lines and comments)
 */
function getSourceFileLineCount(absolutePath) {
    try {
        if (!fs.existsSync(absolutePath)) {
            return null;
        }
        const content = fs.readFileSync(absolutePath, 'utf-8');
        const lines = content.split('\n');
        // Count non-empty, non-comment lines
        let count = 0;
        let inBlockComment = false;
        for (const line of lines) {
            const trimmed = line.trim();
            if (inBlockComment) {
                if (trimmed.includes('*/')) {
                    inBlockComment = false;
                }
                continue;
            }
            if (trimmed.startsWith('/*')) {
                // Check if the block comment ends on the same line (inline block comment)
                if (!trimmed.includes('*/')) {
                    inBlockComment = true;
                }
                continue;
            }
            if (trimmed === '' || trimmed.startsWith('//') || trimmed.startsWith('*')) {
                continue;
            }
            count++;
        }
        return count;
    } catch {
        return null;
    }
}

/**
 * Count expect() calls in a client test file
 */
function countClientExpects(sourceFilePath) {
    // Convert source file path to spec file path
    const specFilePath = sourceFilePath.replace('.ts', '.spec.ts');
    const absolutePath = path.join(PROJECT_ROOT, 'src/main/webapp/app', specFilePath);

    try {
        if (!fs.existsSync(absolutePath)) {
            return null;
        }
        const content = fs.readFileSync(absolutePath, 'utf-8');
        // Count expect( calls - the standard Jest/Jasmine assertion
        const matches = content.match(/expect\s*\(/g);
        return matches ? matches.length : 0;
    } catch {
        return null;
    }
}

/**
 * Count assertions in a test file content
 */
function countAssertionsInContent(content) {
    const assertThatMatches = content.match(/assertThat\s*\(/g) || [];
    const assertEqualsMatches = content.match(/assertEquals\s*\(/g) || [];
    const assertTrueMatches = content.match(/assertTrue\s*\(/g) || [];
    const assertFalseMatches = content.match(/assertFalse\s*\(/g) || [];
    const assertNullMatches = content.match(/assertNull\s*\(/g) || [];
    const assertNotNullMatches = content.match(/assertNotNull\s*\(/g) || [];
    const assertThrowsMatches = content.match(/assertThrows\s*\(/g) || [];
    const verifyMatches = content.match(/verify\s*\(/g) || [];

    return (
        assertThatMatches.length +
        assertEqualsMatches.length +
        assertTrueMatches.length +
        assertFalseMatches.length +
        assertNullMatches.length +
        assertNotNullMatches.length +
        assertThrowsMatches.length +
        verifyMatches.length
    );
}

/**
 * Count assert calls in server test files for a given source file
 */
function countServerAsserts(sourceFilePath) {
    // e.g., de/tum/cit/aet/artemis/core/web/admin/AdminCourseResource.java
    const fileName = sourceFilePath.split('/').pop().replace('.java', '');

    // Extract the base name without common suffixes for broader matching
    // e.g., AdminCourseResource -> Course, CourseRepository -> Course, CourseService -> Course
    let baseName = fileName
        .replace(/^Admin/, '') // AdminCourseResource -> CourseResource
        .replace(/Repository$/, '')
        .replace(/Service$/, '')
        .replace(/Resource$/, '')
        .replace(/DTO$/, '')
        .replace(/Controller$/, '');

    // Also try the direct class name
    const directName = fileName;

    // Build search patterns - look for test files that might test this class
    // 1. Direct match: CourseRequestService -> CourseRequestServiceTest, CourseRequestServiceIntegrationTest
    // 2. Base name match: CourseRepository -> CourseIntegrationTest, CourseTest
    // 3. Entity tests: Course.java -> CourseTest, CourseIntegrationTest
    const testPatterns = new Set([
        // Direct patterns
        `${directName}Test.java`,
        `${directName}IntegrationTest.java`,
        `${directName}UnitTest.java`,
        // Base name patterns (for repositories, services tested via integration tests)
        `${baseName}Test.java`,
        `${baseName}IntegrationTest.java`,
        `${baseName}UnitTest.java`,
    ]);

    let totalAsserts = 0;
    let foundTestFile = false;
    const processedFiles = new Set();

    const testDir = path.join(PROJECT_ROOT, 'src/test/java');

    for (const pattern of testPatterns) {
        const testFiles = findFilesRecursively(testDir, pattern);

        for (const testFile of testFiles) {
            // Avoid counting the same file twice
            if (processedFiles.has(testFile)) {
                continue;
            }
            processedFiles.add(testFile);

            try {
                const content = fs.readFileSync(testFile, 'utf-8');

                // Check if this test file actually references the class we're looking for
                // This avoids false matches (e.g., CourseTest matching for DiscourseService)
                const classNamePattern = new RegExp(`\\b${directName}\\b`);
                if (!classNamePattern.test(content)) {
                    continue;
                }

                foundTestFile = true;
                totalAsserts += countAssertionsInContent(content);
            } catch {
                // Continue
            }
        }
    }

    // If no direct test file found, try to find any test file that imports/uses this class
    if (!foundTestFile) {
        const allTestFiles = findTestFilesInModule(sourceFilePath);
        for (const testFile of allTestFiles) {
            if (processedFiles.has(testFile)) {
                continue;
            }

            try {
                const content = fs.readFileSync(testFile, 'utf-8');

                // Check if this test imports or references our class
                const importPattern = new RegExp(`import.*\\.${directName};`);
                const usagePattern = new RegExp(`\\b${directName}\\b`);

                if (importPattern.test(content) || usagePattern.test(content)) {
                    processedFiles.add(testFile);
                    foundTestFile = true;
                    totalAsserts += countAssertionsInContent(content);
                }
            } catch {
                // Continue
            }
        }
    }

    return foundTestFile ? totalAsserts : null;
}

/**
 * Find all test files in the same module as the source file
 */
function findTestFilesInModule(sourceFilePath) {
    // e.g., de/tum/cit/aet/artemis/core/repository/CourseRepository.java
    // -> Look in src/test/java/de/tum/cit/aet/artemis/core/
    const parts = sourceFilePath.split('/');
    const artemisIndex = parts.indexOf('artemis');
    if (artemisIndex === -1 || artemisIndex + 1 >= parts.length) {
        return [];
    }

    const moduleName = parts[artemisIndex + 1]; // e.g., 'core'
    const moduleTestDir = path.join(PROJECT_ROOT, 'src/test/java/de/tum/cit/aet/artemis', moduleName);

    return findAllJavaTestFiles(moduleTestDir);
}

/**
 * Find all Java test files in a directory
 */
function findAllJavaTestFiles(dir) {
    const results = [];
    try {
        const entries = fs.readdirSync(dir, { withFileTypes: true });
        for (const entry of entries) {
            const fullPath = path.join(dir, entry.name);
            if (entry.isDirectory()) {
                results.push(...findAllJavaTestFiles(fullPath));
            } else if (entry.name.endsWith('Test.java') || entry.name.endsWith('IntegrationTest.java')) {
                results.push(fullPath);
            }
        }
    } catch {
        // Directory doesn't exist or not accessible
    }
    return results;
}

/**
 * Recursively find files matching a pattern
 */
function findFilesRecursively(dir, fileName) {
    const results = [];
    try {
        const entries = fs.readdirSync(dir, { withFileTypes: true });
        for (const entry of entries) {
            const fullPath = path.join(dir, entry.name);
            if (entry.isDirectory()) {
                results.push(...findFilesRecursively(fullPath, fileName));
            } else if (entry.name === fileName) {
                results.push(fullPath);
            }
        }
    } catch {
        // Directory doesn't exist or not accessible
    }
    return results;
}

/**
 * Build coverage table for client files
 */
function buildClientCoverageTable(clientFiles, options) {
    if (Object.keys(clientFiles).length === 0) {
        return null;
    }

    if (!fs.existsSync(CLIENT_COVERAGE_SUMMARY)) {
        return 'Coverage data not found. Run tests first or check if coverage-summary.json exists.';
    }

    let coverageSummary;
    try {
        coverageSummary = JSON.parse(fs.readFileSync(CLIENT_COVERAGE_SUMMARY, 'utf-8'));
    } catch (err) {
        return `Failed to parse coverage data: ${err.message}`;
    }

    const rows = [];
    for (const [filePath, changeType] of Object.entries(clientFiles)) {
        const fileName = filePath.split('/').pop();

        // Skip spec files
        if (fileName.endsWith('.spec.ts')) {
            continue;
        }

        const coverage = getClientFileCoverage(filePath, coverageSummary);
        const absoluteSourcePath = path.join(PROJECT_ROOT, 'src/main/webapp/app', filePath);
        const lineCount = getSourceFileLineCount(absoluteSourcePath);
        const expectCount = countClientExpects(filePath);

        // Calculate ratio: expects per 100 lines of source code
        let ratio = null;
        if (lineCount && lineCount > 0 && expectCount !== null) {
            ratio = (expectCount / lineCount) * 100;
        }

        rows.push({
            file: fileName,
            coverage: coverage || `not found (${changeType})`,
            lineCount: lineCount !== null ? lineCount : '?',
            expectCount: expectCount !== null ? expectCount : '?',
            ratio: ratio !== null ? ratio.toFixed(1) : '?',
        });
    }

    if (rows.length === 0) {
        return null;
    }

    let table = '| Class/File | Line Coverage | Lines | Expects | Ratio |\n';
    table += '|------------|-------------:|------:|--------:|------:|\n';
    for (const row of rows) {
        table += `| ${row.file} | ${row.coverage} | ${row.lineCount} | ${row.expectCount} | ${row.ratio} |\n`;
    }

    return table;
}

/**
 * Build coverage table for server files
 */
function buildServerCoverageTable(serverFiles, serverModules, options) {
    if (Object.keys(serverFiles).length === 0) {
        return null;
    }

    const rows = [];
    for (const [filePath, changeType] of Object.entries(serverFiles)) {
        const fileName = filePath.split('/').pop();

        // Determine which module this file belongs to
        const afterArtemis = filePath.replace('de/tum/cit/aet/artemis/', '');
        const moduleName = afterArtemis.split('/')[0];

        const coverage = getServerFileCoverage(filePath, moduleName);
        const absoluteSourcePath = path.join(PROJECT_ROOT, 'src/main/java', filePath);
        const lineCount = getSourceFileLineCount(absoluteSourcePath);

        rows.push({
            file: fileName,
            coverage: coverage || `not found (${changeType})`,
            lineCount: lineCount !== null ? lineCount : '?',
        });
    }

    if (rows.length === 0) {
        return null;
    }

    let table = '| Class/File | Line Coverage | Lines |\n';
    table += '|------------|-------------:|------:|\n';
    for (const row of rows) {
        table += `| ${row.file} | ${row.coverage} | ${row.lineCount} |\n`;
    }

    return table;
}

/**
 * Copy text to clipboard (cross-platform)
 */
function copyToClipboard(text) {
    try {
        const platform = process.platform;
        if (platform === 'darwin') {
            execSync('pbcopy', { input: text });
        } else if (platform === 'linux') {
            try {
                execSync('xclip -selection clipboard', { input: text });
            } catch {
                execSync('xsel --clipboard --input', { input: text });
            }
        } else if (platform === 'win32') {
            execSync('clip', { input: text });
        } else {
            return false;
        }
        return true;
    } catch {
        return false;
    }
}

/**
 * Main function
 */
async function main() {
    const options = parseArgs();

    if (options.help) {
        showHelp();
        process.exit(0);
    }

    console.log('\n📊 Local PR Coverage Report Generator\n');

    // Check if modules are explicitly specified for testing
    const hasExplicitClientModules = options.clientModules.length > 0;
    const hasExplicitServerModules = options.serverModules.length > 0;

    // Step 1: Always get changed files (needed for coverage report filtering)
    info(`Comparing against ${options.baseBranch}...`);
    const changedFiles = getChangedFiles(options.baseBranch, options);

    const totalChanges = Object.keys(changedFiles).length;
    if (totalChanges === 0) {
        info('No changed files found');
        process.exit(0);
    }
    info(`Found ${totalChanges} changed files`);

    // Step 2: Categorize changed files
    const categorized = categorizeChangedFiles(changedFiles, options);
    const { clientFiles, serverFiles } = categorized;

    // Modules to test: use explicit if specified, otherwise auto-detected
    let clientModulesToTest = hasExplicitClientModules ? options.clientModules : categorized.clientModules;
    let serverModulesToTest = hasExplicitServerModules ? options.serverModules : categorized.serverModules;

    if (hasExplicitClientModules) {
        info(`Using explicitly specified client modules for testing: ${clientModulesToTest.join(', ')}`);
    }
    if (hasExplicitServerModules) {
        info(`Using explicitly specified server modules for testing: ${serverModulesToTest.join(', ')}`);
    }

    info(`Client: ${Object.keys(clientFiles).length} changed files, testing ${clientModulesToTest.length} modules (${clientModulesToTest.join(', ') || 'none'})`);
    info(`Server: ${Object.keys(serverFiles).length} changed files, testing ${serverModulesToTest.length} modules (${serverModulesToTest.join(', ') || 'none'})`);

    // Step 3: Run tests if not skipped
    if (!options.skipTests) {
        console.log('');

        if (!options.serverOnly && clientModulesToTest.length > 0) {
            const clientSuccess = await runClientTests(clientModulesToTest, options);
            if (!clientSuccess) {
                error('Client tests failed');
                process.exit(1);
            }
        }

        if (!options.clientOnly && serverModulesToTest.length > 0) {
            const serverSuccess = await runServerTests(serverModulesToTest, options);
            if (!serverSuccess) {
                error('Server tests failed');
                process.exit(1);
            }
        }
    } else {
        info('Skipping tests, using existing coverage data');
    }

    // Step 4: Build coverage tables (always based on changed files, not all files in modules)
    console.log('\n📋 Building coverage report...\n');

    let result = '';

    if (!options.serverOnly) {
        const clientTable = buildClientCoverageTable(clientFiles, options);
        if (clientTable) {
            result += `#### Client\n\n${clientTable}\n`;
        }
    }

    if (!options.clientOnly) {
        const serverTable = buildServerCoverageTable(serverFiles, categorized.serverModules, options);
        if (serverTable) {
            result += `#### Server\n\n${serverTable}\n`;
        }
    }

    if (!result) {
        info('No coverage data to report');
        process.exit(0);
    }

    // Step 5: Output results
    console.log('─'.repeat(60));
    console.log(result);
    console.log('─'.repeat(60));

    if (!options.print) {
        if (copyToClipboard(result)) {
            success('Coverage report copied to clipboard!');
        } else {
            warn('Could not copy to clipboard. Use --print to print to console.');
        }
    }

    console.log('');
}

main().catch((err) => {
    error(err.message);
    process.exit(1);
});
