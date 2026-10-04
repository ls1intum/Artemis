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
    /** Screenshots of the current user interface, shown below the description. */
    readonly images?: readonly string[];
}

const STUDENT_IMAGES = '/content/images/feature-overview/students/';
const INSTRUCTOR_IMAGES = '/content/images/feature-overview/instructors/';

export const STUDENT_FEATURES: readonly ExamFeature[] = [
    { key: 'conduction', icon: faPlay, images: [STUDENT_IMAGES + 'exam_overview.webp'] },
    { key: 'examMode', icon: faFilePen, images: [STUDENT_IMAGES + 'exam_start.webp'] },
    { key: 'offline', icon: faSignal },
    { key: 'userInterface', icon: faPuzzlePiece },
    { key: 'codeEditor', icon: faCode, images: [STUDENT_IMAGES + 'code_editor.webp'] },
    { key: 'localIDE', icon: faCodeBranch },
    { key: 'apollonEditor', icon: faObjectGroup, images: [STUDENT_IMAGES + 'modeling_editor.webp'] },
    { key: 'textEditor', icon: faFile, images: [STUDENT_IMAGES + 'text_editor.webp'] },
    { key: 'quizExercises', icon: faCheckSquare, images: [STUDENT_IMAGES + 'quiz_exercise.webp'] },
    { key: 'exerciseUpdateNotification', icon: faBell, images: [STUDENT_IMAGES + 'exercise_update_notification.webp', STUDENT_IMAGES + 'exercise_diff_view.webp'] },
    { key: 'summary', icon: faThList, images: [STUDENT_IMAGES + 'summary.webp'] },
    { key: 'qualityAndFair', icon: faEye },
    { key: 'onlineReview', icon: faComment },
    { key: 'gradeKey', icon: faFileAlt },
    { key: 'login', icon: faSignInAlt, tumOnly: true },
];

export const INSTRUCTOR_FEATURES: readonly ExamFeature[] = [
    { key: 'createConductAssess', icon: faCubes },
    { key: 'configuration', icon: faCogs, images: [INSTRUCTOR_IMAGES + 'exam_configuration.webp'] },
    { key: 'examMode', icon: faFilePen },
    { key: 'exerciseTypes', icon: faTasks },
    { key: 'exerciseVariants', icon: faCopy, images: [INSTRUCTOR_IMAGES + 'exercise_groups.webp'] },
    { key: 'testRunConduction', icon: faPencilAlt },
    { key: 'examExerciseUpdates', icon: faWrench },
    { key: 'sessionMonitoring', icon: faHdd, images: [INSTRUCTOR_IMAGES + 'suspicious_sessions.webp'] },
    { key: 'plagiarismDetection', icon: faUserSecret },
    { key: 'anonymousAssessment', icon: faShieldAlt, images: [INSTRUCTOR_IMAGES + 'anonymous_assessment.webp'] },
    { key: 'automaticAssessment', icon: faMagic },
    { key: 'reviewIndividualExams', icon: faSearchPlus, images: [INSTRUCTOR_IMAGES + 'student_exams.webp'] },
    { key: 'assessmentMonitoring', icon: faMicrochip, images: [INSTRUCTOR_IMAGES + 'assessment_dashboard.webp'] },
    { key: 'complaints', icon: faQuestion },
    { key: 'statistics', icon: faChartPie },
    { key: 'checklist', icon: faClipboardCheck, images: [INSTRUCTOR_IMAGES + 'exam_checklist.webp'] },
    { key: 'gradeKey', icon: faFileAlt, images: [INSTRUCTOR_IMAGES + 'grade_key.webp'] },
    { key: 'submissionPolicy', icon: faBan },
];
