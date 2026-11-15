const path = require('node:path');
const config = require('./stylelint.config.json');

// Resolve override globs from the repository root for both local and hosted-tool discovery.
const repoRoot = path.resolve(__dirname, '../..');

module.exports = {
    ...config,
    overrides: config.overrides.map((override) => ({
        ...override,
        files: override.files.map((file) => path.resolve(repoRoot, file)),
    })),
};
