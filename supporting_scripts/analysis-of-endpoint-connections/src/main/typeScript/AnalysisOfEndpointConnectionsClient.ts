import { readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { Preprocessor } from './Preprocessor';
import { Postprocessor } from './Postprocessor';
import { writeFileSync } from 'node:fs';

/**
 * Recursively collects all TypeScript files in a directory.
 *
 * @param dir - The directory to search for TypeScript files.
 * @param files - An array to store the collected TypeScript file paths.
 * @returns An array of TypeScript file paths relative to 'src/main/webapp/app'.
 */
function collectTypeScriptFiles(dir: string, files: string[] = []) : string[] {
    const entries = readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
        const fullPath = join(dir, entry.name);

        const filePathFromSrcFolder = fullPath.substring(fullPath.indexOf('src/main/webapp/app'));
        if (entry.isDirectory()) {
            collectTypeScriptFiles(fullPath, files);
        } else if (entry.isFile() && entry.name.endsWith('.ts')) {
            files.push(filePathFromSrcFolder);
        }
    }
    return files;
}

const clientDirPath = resolve('src/main/webapp/app');

const tsFiles = collectTypeScriptFiles(clientDirPath);

// preprocess each file
tsFiles.forEach((filePath) => {
    const preProcessor = new Preprocessor(filePath);
    preProcessor.preprocessFile();
});

// postprocess each file
tsFiles.forEach((filePath) => {
    const postProcessor = new Postprocessor(filePath);
    postProcessor.extractRestCalls();
});

try {
    console.log('Working directory:', process.cwd());
    writeFileSync('supporting_scripts/analysis-of-endpoint-connections/restCalls.json', JSON.stringify(Postprocessor.filesWithRestCalls, null, 2));
} catch (error) {
    console.error('Failed to write REST calls to file:', error);
}
