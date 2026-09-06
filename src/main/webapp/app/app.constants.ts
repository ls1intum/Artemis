import { __DEBUG_INFO_ENABLED__, __VERSION__ } from 'app/core/environments/environment';

export const VERSION = __VERSION__;
export const DEBUG_INFO_ENABLED = __DEBUG_INFO_ENABLED__;

export const MIN_SCORE_GREEN = 80;
export const MIN_SCORE_ORANGE = 40;

// NOTE: those values have to be the same as in Constants.java
export const USERNAME_MIN_LENGTH = 4;
export const USERNAME_MAX_LENGTH = 50;
export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 50;

export const EXAM_START_WAIT_TIME_MINUTES = 5;

export const SCORE_PATTERN = '^[0-9]{1,2}$|^100$';

export const ARTEMIS_DEFAULT_COLOR = '#3E8ACC';
export const ARTEMIS_VERSION_HEADER = 'Content-Version';

/**
 * Turns a served file reference into the URL it is requested under.
 *
 * The server assembles every file reference it sends from a hardcoded template plus the owning entity (`PublicFileUrl` and `ServedFileUrl` on the server), but it sends the
 * result one segment narrower than the real URL: `PublicFileUrl#clientPath` strips the leading `files/` because this function puts `api/core/files/` back. The two halves are a
 * single contract, so a call site that stops prefixing a value the server sent requests a path that does not exist.
 *
 * **Why the client still prefixes at all.** The natural end state is that the server sends the whole URL and this function disappears. That is a change to the value of a JSON
 * field rather than to a path, and unlike a path a field has no room for a deprecated alias: there is one value and every client reads it. The mobile apps and the VS Code
 * extension prepend their own base URL and this same prefix, so a server that started sending complete URLs would make all of them build `api/core/files/api/core/files/...`.
 * The REST guideline is explicit that those clients cannot be updated in lockstep (see `documentation/docs/developer/guidelines/rest-api.mdx`), which is also why the previous
 * spellings of the file paths are still served (see `CoreLegacyFileRestPaths` on the server). Removing this function is therefore a later release's change, once those clients
 * read the field without prefixing.
 *
 * An already prefixed value is passed through unchanged, so that switching the server over is a pure server change: a browser tab still running this release against a server
 * that has been upgraded past it keeps rendering its images instead of silently doubling the prefix.
 *
 * @param filePath a file reference as the server sent it, relative to {@link FILES_PATH_PREFIX}
 * @returns the URL to request the file under, or undefined when there is no reference
 */
export const addPublicFilePrefix = (filePath?: string): string | undefined => {
    if (!filePath) {
        return undefined;
    }
    if (filePath.startsWith('blob') || filePath.startsWith('/public/') || filePath.startsWith('http') || filePath.startsWith(FILES_PATH_PREFIX)) {
        // Already an absolute URL, a static resource, a locally held preview, or a complete served URL: nothing to prepend
        return filePath;
    }
    return `${FILES_PATH_PREFIX}${filePath}`;
};

/**
 * The prefix every stored file is served under, which is the `api/core/` request mapping of the server's `FileResource` plus its `files/` segment.
 */
export const FILES_PATH_PREFIX = 'api/core/files/';

export const MODULE_FEATURE_PASSKEY = 'passkey';

export const MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN = 'passkey-admin';

export const MODULE_FEATURE_ATLAS = 'atlas';

export const MODULE_FEATURE_HYPERION = 'hyperion';

export const MODULE_FEATURE_DEIMOS = 'deimos';

export const MODULE_FEATURE_IRIS = 'iris';

export const MODULE_FEATURE_EXAM = 'exam';

export const MODULE_FEATURE_PLAGIARISM = 'plagiarism';

export const MODULE_FEATURE_TEXT = 'text';

export const MODULE_FEATURE_MODELING = 'modeling';

export const MODULE_FEATURE_FILEUPLOAD = 'fileupload';

export const MODULE_FEATURE_LECTURE = 'lecture';

export const MODULE_FEATURE_TUTORIALGROUP = 'tutorialgroup';

export const MODULE_FEATURE_SHARING = 'sharing';

export const MODULE_FEATURE_LTI = 'lti';

export const MODULE_FEATURE_ATHENA = 'athena';

export const MODULE_FEATURE_APOLLON = 'apollon';

export const MODULE_FEATURE_LDAP = 'ldap';

export const MODULE_FEATURE_SAML2 = 'saml2';

export const MODULE_FEATURE_THEIA = 'theia';

export type ModuleFeature =
    | typeof MODULE_FEATURE_PASSKEY
    | typeof MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN
    | typeof MODULE_FEATURE_ATLAS
    | typeof MODULE_FEATURE_HYPERION
    | typeof MODULE_FEATURE_DEIMOS
    | typeof MODULE_FEATURE_IRIS
    | typeof MODULE_FEATURE_EXAM
    | typeof MODULE_FEATURE_PLAGIARISM
    | typeof MODULE_FEATURE_TEXT
    | typeof MODULE_FEATURE_MODELING
    | typeof MODULE_FEATURE_FILEUPLOAD
    | typeof MODULE_FEATURE_LECTURE
    | typeof MODULE_FEATURE_TUTORIALGROUP
    | typeof MODULE_FEATURE_SHARING
    | typeof MODULE_FEATURE_LTI
    | typeof MODULE_FEATURE_ATHENA
    | typeof MODULE_FEATURE_APOLLON
    | typeof MODULE_FEATURE_LDAP
    | typeof MODULE_FEATURE_SAML2
    | typeof MODULE_FEATURE_THEIA;

export const PROFILE_LOCALCI = 'localci';

export const PROFILE_BUILDAGENT = 'buildagent';

export const PROFILE_LTI = 'lti';

export const PROFILE_PROD = 'prod';

export const PROFILE_DEV = 'dev';

export const PROFILE_TEST = 'test';

export const PROFILE_JENKINS = 'jenkins';

export type ProfileFeature =
    typeof PROFILE_LOCALCI | typeof PROFILE_BUILDAGENT | typeof PROFILE_LTI | typeof PROFILE_PROD | typeof PROFILE_DEV | typeof PROFILE_TEST | typeof PROFILE_JENKINS;
