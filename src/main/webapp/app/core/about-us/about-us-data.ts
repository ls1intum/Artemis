import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import {
    faBug,
    faChalkboardUser,
    faCode,
    faCodePullRequest,
    faComments,
    faEnvelope,
    faFileSignature,
    faLightbulb,
    faListCheck,
    faRobot,
    faRoute,
    faSquarePlus,
    faStopwatch,
    faWandMagicSparkles,
} from '@fortawesome/free-solid-svg-icons';
import {
    MODULE_FEATURE_ATHENA,
    MODULE_FEATURE_ATLAS,
    MODULE_FEATURE_DEIMOS,
    MODULE_FEATURE_EXAM,
    MODULE_FEATURE_FILEUPLOAD,
    MODULE_FEATURE_HYPERION,
    MODULE_FEATURE_IRIS,
    MODULE_FEATURE_LECTURE,
    MODULE_FEATURE_LTI,
    MODULE_FEATURE_MODELING,
    MODULE_FEATURE_PASSKEY,
    MODULE_FEATURE_PLAGIARISM,
    MODULE_FEATURE_SAML2,
    MODULE_FEATURE_SHARING,
    MODULE_FEATURE_TEXT,
    MODULE_FEATURE_THEIA,
    MODULE_FEATURE_TUTORIALGROUP,
    ModuleFeature,
} from 'app/app.constants';

/**
 * A feature the About page highlights, named after its entry in the feature catalogue ({@code UserFeature} on the server).
 * Name and description come from the catalogue translations, so the page and the admin feature usage page describe it alike.
 */
export interface AboutUsHighlight {
    /** The {@code UserFeature} constant, which keys {@code artemisApp.featureUsage.catalog.feature.*}. */
    readonly catalogueKey: string;
    readonly icon: IconDefinition;
    /** The module that must be enabled for the feature to exist on this installation; absent for features every installation has. */
    readonly module?: ModuleFeature;
    /**
     * Key below {@code artemisApp.aboutUsOverview.highlights.features} for a feature whose catalogue entry is written for administrators, or which the
     * catalogue splits into several entries. The page then names and describes it with these texts instead.
     */
    readonly translationKey?: string;
}

/** The translation key prefix that names ({@code .name}) and describes ({@code .description}) a highlight. */
export function highlightTranslationBase(highlight: AboutUsHighlight): string {
    return highlight.translationKey
        ? `artemisApp.aboutUsOverview.highlights.features.${highlight.translationKey}`
        : `artemisApp.featureUsage.catalog.feature.${highlight.catalogueKey}`;
}

/** Candidates in display order. The page shows the first {@link MAX_HIGHLIGHTS} whose module is enabled. */
export const HIGHLIGHTS: readonly AboutUsHighlight[] = [
    { catalogueKey: 'PROGRAMMING_ONLINE_EDITOR', icon: faCode },
    { catalogueKey: 'PROGRAMMING_RESULTS', icon: faListCheck },
    { catalogueKey: 'EXAM_TAKE', icon: faFileSignature, module: MODULE_FEATURE_EXAM },
    { catalogueKey: 'QUIZ_LIVE', icon: faStopwatch },
    { catalogueKey: 'LEARNING_PATHS', icon: faRoute, module: MODULE_FEATURE_ATLAS },
    { catalogueKey: 'MESSAGING', icon: faComments },
    { catalogueKey: 'IRIS_CHAT', icon: faRobot, module: MODULE_FEATURE_IRIS, translationKey: 'iris' },
    { catalogueKey: 'AI_FEEDBACK_REQUEST', icon: faLightbulb, module: MODULE_FEATURE_ATHENA, translationKey: 'aiFeedback' },
    { catalogueKey: 'HYPERION_PROBLEM_STATEMENT', icon: faWandMagicSparkles, module: MODULE_FEATURE_HYPERION, translationKey: 'exerciseGeneration' },
    { catalogueKey: 'LECTURE_PAGES', icon: faChalkboardUser, module: MODULE_FEATURE_LECTURE },
];

/** Three rows of three: with every module enabled, the six features all installations have and the three AI features. */
export const MAX_HIGHLIGHTS = 9;

/**
 * The optional modules the page lists when they are enabled, in display order, each with its key below
 * {@code artemisApp.aboutUsOverview.modules}. Flags that do not change what users can do (LDAP synchronization, the
 * Apollon conversion service, the Atlas sub-features, the administrator passkey requirement) are deliberately absent.
 */
export const MODULES: readonly { readonly feature: ModuleFeature; readonly translationKey: string }[] = [
    { feature: MODULE_FEATURE_EXAM, translationKey: 'exam' },
    { feature: MODULE_FEATURE_TEXT, translationKey: 'text' },
    { feature: MODULE_FEATURE_MODELING, translationKey: 'modeling' },
    { feature: MODULE_FEATURE_FILEUPLOAD, translationKey: 'fileUpload' },
    { feature: MODULE_FEATURE_LECTURE, translationKey: 'lecture' },
    { feature: MODULE_FEATURE_TUTORIALGROUP, translationKey: 'tutorialGroup' },
    { feature: MODULE_FEATURE_ATLAS, translationKey: 'atlas' },
    { feature: MODULE_FEATURE_IRIS, translationKey: 'iris' },
    { feature: MODULE_FEATURE_ATHENA, translationKey: 'athena' },
    { feature: MODULE_FEATURE_HYPERION, translationKey: 'hyperion' },
    { feature: MODULE_FEATURE_PLAGIARISM, translationKey: 'plagiarism' },
    { feature: MODULE_FEATURE_DEIMOS, translationKey: 'deimos' },
    { feature: MODULE_FEATURE_THEIA, translationKey: 'theia' },
    { feature: MODULE_FEATURE_SHARING, translationKey: 'sharing' },
    { feature: MODULE_FEATURE_LTI, translationKey: 'lti' },
    { feature: MODULE_FEATURE_SAML2, translationKey: 'saml2' },
    { feature: MODULE_FEATURE_PASSKEY, translationKey: 'passkey' },
];

const ISSUE_BASE_URL = 'https://github.com/ls1intum/Artemis/issues/new?projects=ls1intum/1';

export const BUG_REPORT_URL = `${ISSUE_BASE_URL}&labels=bug&template=bug-report.yml`;
export const FEATURE_REQUEST_URL = `${ISSUE_BASE_URL}&labels=feature&template=feature-request.yml`;

export const ICONS = { bug: faBug, feature: faCodePullRequest, contact: faEnvelope, courseRequest: faSquarePlus } as const;

export const PROJECT_LINKS: readonly { readonly translationKey: string; readonly url: string }[] = [
    { translationKey: 'documentation', url: 'https://docs.artemis.tum.de' },
    { translationKey: 'sourceCode', url: 'https://github.com/ls1intum/Artemis' },
    { translationKey: 'securityPolicy', url: 'https://github.com/ls1intum/Artemis/security/policy' },
    { translationKey: 'publications', url: 'https://docs.artemis.tum.de/publications' },
    { translationKey: 'comparison', url: 'https://docs.artemis.tum.de/compare' },
];

export const CONTRIBUTORS_URL = 'https://github.com/ls1intum/Artemis/graphs/contributors';

/** The preferred citation from CITATION.cff in the repository root. */
export const CITATION = {
    text: 'Stephan Krusche and Andreas Seitz. 2018. ArTEMiS: An Automatic Assessment Management System for Interactive Learning. In Proceedings of the 49th ACM Technical Symposium on Computer Science Education (SIGCSE ’18). ACM, 284–289.',
    doiUrl: 'https://doi.org/10.1145/3159450.3159602',
} as const;
