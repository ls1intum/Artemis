const fs = require('fs');
const path = require('path');
const tsconfig = require('../tsconfig.json');

module.exports = {
    parseVersion,
    root,
    mapTypescriptAliasToWebpackAlias
};

// Returns the second occurrence of the version number from `build.gradle` file
function parseVersion() {
    const versionRegex = /^version\s*=\s*[',"]([^',"]*)[',"]/gm; // Match and group the version number
    const buildGradle = fs.readFileSync('build.gradle', 'utf8');
    return versionRegex.exec(buildGradle)[1];
}

const _root = path.resolve(__dirname, '..');

function root(args) {
    args = Array.prototype.slice.call(arguments, 0);
    return path.join.apply(path, [_root].concat(args));
}

function mapTypescriptAliasToWebpackAlias(alias = {}) {
    const webpackAliases = { ...alias };
    Object.entries(tsconfig.compilerOptions.paths)
        .filter(([key, value]) => {
            // use Typescript alias in Webpack only if this has value
            if (value.length) {
                return true;
            }
            return false;
        })
        .map(([key, value]) => {
            // if Typescript alias ends with /* then remove this for Webpack
            const regexToReplace = /\/\*$/;
            const aliasKey = key.replace(regexToReplace, '');
            const aliasValue = value[0].replace(regexToReplace, '');
            return [aliasKey, root(aliasValue)];
        })
        .reduce((aliases, [key, value]) => {
            aliases[key] = value;
            return aliases;
        }, webpackAliases);
    return webpackAliases;
}
