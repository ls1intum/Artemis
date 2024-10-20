import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { Preprocessor } from './Preprocessor';
import { Postprocessor } from './Postprocessor';
import { parse, TSESTree } from '@typescript-eslint/typescript-estree';
import { load } from 'js-yaml';

interface Config {
    excludedEndpointFiles: string[];
    excludedEndpoints: string[];
    excludedRestCallFiles: string[];
    excludedRestCalls: string[];

    endpointParsingResultPath: string;
    restCallParsingResultPath: string;
    endpointAnalysisResultPath: string;
    restCallAnalysisResultPath: string;
    clientDirPath: string;
}

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
        if (entry.isDirectory()) {
            collectTypeScriptFiles(fullPath, files);
        } else if (entry.isFile() && entry.name.endsWith('.ts')) {
            files.push(fullPath);
        }
    }
    return files;
}

/**
 * Parses a TypeScript file and returns its Abstract Syntax Tree (AST).
 *
 * @param filePath - The path to the TypeScript file to be parsed.
 * @returns The TSESTree of the parsed TypeScript file.
 */
function parseTypeScriptFile(filePath: string): TSESTree.Program | null {
    const code = readFileSync(filePath, 'utf8');
    try {
        return parse(code, {
            loc: true,
            comment: true,
            tokens: true,
            ecmaVersion: 2020,
            sourceType: 'module',
        });
    } catch (error) {
        console.error(`Failed to parse TypeScript file at ${filePath}:`, error);
        console.error('Please make sure the file is valid TypeScript code.');
        return null;
    }
}

function readConfigFile(filePath: string): Config | undefined {
    try {
        const fileContent = readFileSync(filePath, 'utf8');
        return load(fileContent) as Config;
    } catch (error) {
        console.error('Failed to read config file:', error);
        return undefined;
    }
}

const configFilePath = 'supporting_scripts/analysis-of-endpoint-connections/analysisOfEndpointConnections.config.yml';

const config = readConfigFile(configFilePath);
if (!config) {
    process.exit(1);
}


const tsFiles = collectTypeScriptFiles(config.clientDirPath);

// create and store Syntax Tree for each file
const astMap = new Map<string, TSESTree.Program>;
tsFiles.forEach((filePath) => {
    const ast =  parseTypeScriptFile(filePath);
    if (ast) {
        astMap.set(filePath, ast);
    }
});

// preprocess each file
Array.from(astMap.keys()).forEach((filePath: string) => {
    const ast = astMap.get(filePath);
    if (ast) {
        const preProcessor = new Preprocessor(ast);
        preProcessor.preprocessFile();
    }
});

// postprocess each file
Array.from(astMap.keys()).forEach((filePath) => {
    const ast = astMap.get(filePath);
    if (ast) {
        const postProcessor = new Postprocessor(filePath, ast);
        postProcessor.extractRestCallsFromProgram();
    }
});

try {

    writeFileSync(`supporting_scripts/analysis-of-endpoint-connections/${config.restCallParsingResultPath}`, JSON.stringify(Postprocessor.filesWithRestCalls, null, 2));
} catch (error) {
    console.error('Failed to write REST calls to file:', error);
}
