const fs = require('fs');
const path = require('path');

module.exports = {
    parseVersion,
    root
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
