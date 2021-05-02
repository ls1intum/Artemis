const fs = require('fs');
const path = require('path');
const tsconfig = require('../tsconfig.json');

// Returns the second occurrence of the version number from `build.gradle` file
export function parseVersion() {
    const versionRegex = /^version\s*=\s*[',"]([^',"]*)[',"]/gm; // Match and group the version number
    const buildGradle = fs.readFileSync('build.gradle', 'utf8');
    const versionResultArray = versionRegex.exec(buildGradle);
    if (versionResultArray && versionResultArray.length > 0) {
        return versionResultArray[1];
    } else {
        return 'unspecified';
    }
}

const _root = path.resolve(__dirname, '..');

export function root(args: any) {
    args = Array.prototype.slice.call(arguments, 0);
    return path.join.apply(path, [_root].concat(args));
}

export function mapTypescriptAliasToWebpackAlias(alias = {}) {
    const webpackAliases = { ...alias };
    const paths: Map<string, string[]> = tsconfig.compilerOptions.paths;
    Object.entries(paths)
        .filter(([, value]) => {
            // use Typescript alias in Webpack only if this has value
            return !!value?.length;
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
