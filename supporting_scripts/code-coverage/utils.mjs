import path from 'path';
import fs from 'fs';

/**
 * Parse vitest.config.ts to extract module names from include patterns.
 * The vitest.config.ts is the single source of truth for which modules use Vitest.
 *
 * @param {string} projectRoot - The root directory of the project
 * @returns {Set<string>} A set of module names that use Vitest
 */
export function getVitestModules(projectRoot) {
    const vitestConfigPath = path.join(projectRoot, 'vitest.config.ts');
    if (!fs.existsSync(vitestConfigPath)) {
        return new Set();
    }
    const content = fs.readFileSync(vitestConfigPath, 'utf-8');
    // Match patterns like: 'src/main/webapp/app/fileupload/**/*.spec.ts'
    const modulePattern = /src\/main\/webapp\/app\/([a-zA-Z0-9_-]+)\/\*\*/g;
    const modules = new Set();
    let match;
    while ((match = modulePattern.exec(content)) !== null) {
        modules.add(match[1]);
    }
    return modules;
}
