/**
 * This script is executed before the Angular CLI's build script is executed.
 * It adds environment variables to the environment file and merges the i18n files.
 * This way, we don't need a webpack configuration file: It replaces
 * - webpack.DefinePlugin and
 * - MergeJsonWebpackPlugin
 */
import fs from "fs";
import path from "path";
import { hashElement } from "folder-hash";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const languagesHash = await hashElement(path.resolve(__dirname, 'src', 'main', 'webapp', 'i18n'), {
    algo: 'md5',
    encoding: 'hex',
    files: { include: ['*.json'] },
});

/*
 * Needed for client compilations with docker compose, where the 'APP_VERSION' property isn't injected by gradle.
 *
 * Returns the inferred APP_VERSION from 'build.gradle', or 'DEV' if this couldn't be retrieved
 */
function inferVersion() {
    let version = 'DEV';

    try {
        const fs = require('fs');
        const buildGradleFile = 'build.gradle';

        let data = fs.readFileSync(buildGradleFile, 'UTF-8');

        version = data.match(/\nversion\s=\s"(.*)"/);

        version = version[1] ?? 'DEV';
    } catch (error) {
        console.log("Error while retrieving 'APP_VERSION' property");
    }

    return version;
}

const environmentConfig = `// Don't change this file manually, it will be overwritten by the build process!
export const __DEBUG_INFO_ENABLED__ = ${Boolean(process.env.__DEBUG_INFO_ENABLED__)};
export const __VERSION__ = ${JSON.stringify(process.env.APP_VERSION || inferVersion())};
// The root URL for API calls, ending with a '/' - for example: \`"https://www.jhipster.tech:8081/myservice/"\`.
// See usage in webpack.custom.js for more info
// If you use an API server, in \`prod\` mode, you will need to enable CORS
// (see the \`jhipster.cors\` common JHipster property in the \`application-*.yml\` configurations)
export const SERVER_API_URL = '';
export const I18N_HASH = ${JSON.stringify(languagesHash.hash)};
`;
fs.writeFileSync(path.resolve(__dirname, 'src', 'main', 'webapp', 'app', 'environments', 'environment.override.ts'), environmentConfig);
