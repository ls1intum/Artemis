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

export const PROFILE_LOCALCI = 'localci';

export const PROFILE_AEOLUS = 'aeolus';

export const FEATURE_PASSKEY = 'passkey';

export const MODULE_FEATURE_ATLAS = 'atlas';

export const MODULE_FEATURE_EXAM = 'exam';

export const MODULE_FEATURE_PLAGIARISM = 'plagiarism';

export const MODULE_FEATURE_TEXT = 'text';

export const MODULE_FEATURE_TUTORIALGROUP = 'tutorialgroup';

export const PROFILE_IRIS = 'iris';

export const PROFILE_LTI = 'lti';

export const PROFILE_PROD = 'prod';

export const PROFILE_DEV = 'dev';

export const PROFILE_TEST = 'test';

export const PROFILE_JENKINS = 'jenkins';

export const PROFILE_APOLLON = 'apollon';

export const PROFILE_ATHENA = 'athena';

export const PROFILE_THEIA = 'theia';
