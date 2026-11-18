import path from 'path';
import fs from 'fs';

/**
 * Returns the set of client modules that run on Vitest. The entire Angular client now runs on Vitest
 * (Jest has been removed), so every top-level module directory under src/main/webapp/app is a Vitest
 * module. Enumerating the directories keeps this correct without maintaining an explicit list.
 *
 * @param {string} projectRoot - The root directory of the project
 * @returns {Set<string>} A set of module names that use Vitest
 */
export function getVitestModules(projectRoot) {
    const appDir = path.join(projectRoot, 'src/main/webapp/app');
    if (!fs.existsSync(appDir)) {
        return new Set();
    }
    return new Set(
        fs
            .readdirSync(appDir, { withFileTypes: true })
            .filter((entry) => entry.isDirectory())
            .map((entry) => entry.name),
    );
}
