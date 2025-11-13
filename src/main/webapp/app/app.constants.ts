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

export const addPublicFilePrefix = (filePath?: string): string | undefined => {
    if (!filePath) {
        return undefined;
    }
    if (filePath.startsWith('blob')) {
        // We don't need to add the prefix, it's locally stored
        return filePath;
    } else {
        return filePath ? `${FILES_PATH_PREFIX}${filePath}` : undefined;
    }
};

export const FILES_PATH_PREFIX = 'api/core/files/';

export const MODULE_FEATURE_PASSKEY = 'passkey';

export const MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN = 'passkeyRequiredForAdministratorFeatures';

export const MODULE_FEATURE_ATLAS = 'atlas';

export const MODULE_FEATURE_HYPERION = 'hyperion';

export const MODULE_FEATURE_EXAM = 'exam';

export const MODULE_FEATURE_PLAGIARISM = 'plagiarism';

export const MODULE_FEATURE_TEXT = 'text';

export const MODULE_FEATURE_TUTORIALGROUP = 'tutorialgroup';

export const MODULE_FEATURE_NEBULA = 'nebula';

export const MODULE_FEATURE_SHARING = 'sharing';

export type ModuleFeature =
    | typeof MODULE_FEATURE_PASSKEY
    | typeof MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN
    | typeof MODULE_FEATURE_ATLAS
    | typeof MODULE_FEATURE_HYPERION
    | typeof MODULE_FEATURE_EXAM
    | typeof MODULE_FEATURE_PLAGIARISM
    | typeof MODULE_FEATURE_TEXT
    | typeof MODULE_FEATURE_TUTORIALGROUP
    | typeof MODULE_FEATURE_NEBULA
    | typeof MODULE_FEATURE_SHARING;

export const PROFILE_LOCALCI = 'localci';

export const PROFILE_AEOLUS = 'aeolus';

export const PROFILE_IRIS = 'iris';

export const PROFILE_LTI = 'lti';

export const PROFILE_PROD = 'prod';

export const PROFILE_DEV = 'dev';

export const PROFILE_TEST = 'test';

export const PROFILE_JENKINS = 'jenkins';

export const PROFILE_APOLLON = 'apollon';

export const PROFILE_ATHENA = 'athena';

export const PROFILE_THEIA = 'theia';

export const PROFILE_LDAP = 'ldap';

export type ProfileFeature =
    | typeof PROFILE_LOCALCI
    | typeof PROFILE_AEOLUS
    | typeof PROFILE_IRIS
    | typeof PROFILE_LTI
    | typeof PROFILE_PROD
    | typeof PROFILE_DEV
    | typeof PROFILE_TEST
    | typeof PROFILE_JENKINS
    | typeof PROFILE_APOLLON
    | typeof PROFILE_ATHENA
    | typeof PROFILE_THEIA
    | typeof PROFILE_LDAP;
