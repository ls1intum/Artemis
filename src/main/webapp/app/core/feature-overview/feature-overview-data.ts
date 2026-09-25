import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import {
    faBan,
    faBell,
    faChartPie,
    faCheckSquare,
    faClipboardCheck,
    faCode,
    faCodeBranch,
    faCogs,
    faComment,
    faCopy,
    faCubes,
    faEye,
    faFile,
    faFileAlt,
    faFilePen,
    faHdd,
    faMagic,
    faMicrochip,
    faObjectGroup,
    faPencilAlt,
    faPlay,
    faPuzzlePiece,
    faQuestion,
    faSearchPlus,
    faShieldAlt,
    faSignInAlt,
    faSignal,
    faTasks,
    faThList,
    faUserSecret,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';

/** The audience of a feature page. The value is the translation key segment below {@code featureOverview}. */
export enum TargetAudience {
    INSTRUCTORS = 'instructor',
    STUDENTS = 'students',
}

/**
 * An exam feature shown on a feature page. {@code key} names its translations
 * ({@code featureOverview.<audience>.feature.<key>.title|shortDescription|descriptionTextOne}) and doubles as its anchor.
 */
export interface ExamFeature {
    readonly key: string;
    readonly icon: IconDefinition;
    /** Only shown on installations that sign users in with a TUM account. */
    readonly tumOnly?: boolean;
}

export const STUDENT_FEATURES: readonly ExamFeature[] = [
    { key: 'conduction', icon: faPlay },
    { key: 'examMode', icon: faFilePen },
    { key: 'offline', icon: faSignal },
    { key: 'userInterface', icon: faPuzzlePiece },
    { key: 'codeEditor', icon: faCode },
    { key: 'localIDE', icon: faCodeBranch },
    { key: 'apollonEditor', icon: faObjectGroup },
    { key: 'textEditor', icon: faFile },
    { key: 'quizExercises', icon: faCheckSquare },
    { key: 'exerciseUpdateNotification', icon: faBell },
    { key: 'summary', icon: faThList },
    { key: 'qualityAndFair', icon: faEye },
    { key: 'onlineReview', icon: faComment },
    { key: 'gradeKey', icon: faFileAlt },
    { key: 'login', icon: faSignInAlt, tumOnly: true },
];

export const INSTRUCTOR_FEATURES: readonly ExamFeature[] = [
    { key: 'createConductAssess', icon: faCubes },
    { key: 'configuration', icon: faCogs },
    { key: 'examMode', icon: faFilePen },
    { key: 'exerciseTypes', icon: faTasks },
    { key: 'exerciseVariants', icon: faCopy },
    { key: 'testRunConduction', icon: faPencilAlt },
    { key: 'examExerciseUpdates', icon: faWrench },
    { key: 'sessionMonitoring', icon: faHdd },
    { key: 'plagiarismDetection', icon: faUserSecret },
    { key: 'anonymousAssessment', icon: faShieldAlt },
    { key: 'automaticAssessment', icon: faMagic },
    { key: 'reviewIndividualExams', icon: faSearchPlus },
    { key: 'assessmentMonitoring', icon: faMicrochip },
    { key: 'complaints', icon: faQuestion },
    { key: 'statistics', icon: faChartPie },
    { key: 'checklist', icon: faClipboardCheck },
    { key: 'gradeKey', icon: faFileAlt },
    { key: 'submissionPolicy', icon: faBan },
];
