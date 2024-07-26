import { readdirSync } from 'fs';
import { join, resolve } from 'path';
import { Preprocessor } from './Preprocessor';
import { Postprocessor } from './Postprocessor';
import { writeFileSync } from 'node:fs';

// Recursively collect all TypeScript files in a directory
function collectTypeScriptFiles(dir: string, files: string[] = []) {
    const entries = readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
        const fullPath = join(dir, entry.name);

        const filePathFromSrcFolder = fullPath.substring(fullPath.indexOf('src/main/webapp/app') - 'src/main/webapp/app'.length + 'src/main/webapp/app'.length);
        if (entry.isDirectory()) {
            collectTypeScriptFiles(fullPath, files);
        } else if (entry.isFile() && entry.name.endsWith('.ts')) {
            files.push(filePathFromSrcFolder);
        }
    }
    return files;
}

const clientDirPath = resolve('../../../../../src/main/webapp/app');

const tsFiles = collectTypeScriptFiles(clientDirPath);

// preprocess each file
tsFiles.forEach((filePath) => {
    let preProcessor = new Preprocessor(filePath);
    preProcessor.preprocessFile();
});

// postprocess each file
tsFiles.forEach((filePath) => {
    let postProcessor = new Postprocessor(filePath);
    postProcessor.extractRestCalls();
});

// Output REST calls
for (let restCallFile of Postprocessor.filesWithRestCalls) {
    if (restCallFile.restCalls.length > 0) {
        console.log(`REST calls in ${restCallFile.filePath}:`, restCallFile.restCalls);
    }
}

writeFileSync('../../../../../supporting_scripts/analysis-of-endpoint-connections/restCalls.json', JSON.stringify(Postprocessor.filesWithRestCalls, null, 2));
