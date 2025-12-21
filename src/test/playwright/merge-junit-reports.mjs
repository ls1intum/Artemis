/* global console, process */
import { spawnSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const reportsDir = path.join(__dirname, 'test-reports');
const outputFile = path.join(reportsDir, 'results.xml');

if (!fs.existsSync(reportsDir)) {
    console.log('No test-reports directory found; skipping JUnit merge.');
    process.exit(0);
}

const inputFiles = fs
    .readdirSync(reportsDir)
    .filter((name) => name.startsWith('results-') && name.endsWith('.xml'))
    .map((name) => path.join(reportsDir, name));

if (inputFiles.length === 0) {
    console.log('No intermediate JUnit result files found; nothing to merge.');
    process.exit(0);
}

console.log(`Merging ${inputFiles.length} JUnit report(s) into ${outputFile}`);

const result = spawnSync('npx', ['junit-merge', ...inputFiles, '-o', outputFile], { stdio: 'inherit' });
if (result.status !== 0) {
    process.exit(result.status ?? 1);
}

// Clean up intermediate files
for (const file of inputFiles) {
    try {
        fs.unlinkSync(file);
    } catch {
        // ignore
    }
}
