module.exports = {
    I18N_HASH: 'generated_hash',
    SERVER_API_URL: '',
    __VERSION__: process.env.APP_VERSION || inferVersion(),
    __DEBUG_INFO_ENABLED__: false,
};

/*
 * Needed for client compilations with docker-compose, where the 'APP_VERSION' property isn't injected by gradle.
 *
 * Returns the inferred APP_VERSION from 'build.gradle', or 'DEV' if this couldn't be retrieved
 */
function inferVersion() {
    let version = 'DEV';

    try {
        const fs = require('fs');
        const buildGradleFile = 'build.gradle';

        let data = fs.readFileSync(buildGradleFile, 'UTF-8');

        version = data.match(/version\s=\s"(.*)"/);

        version = version[1] ?? 'DEV';
    } catch (error) {
        console.log("Error while retrieving 'APP_VERSION' property");
    }

    return version;
}
