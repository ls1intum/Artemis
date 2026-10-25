import { CourseAvailableTabs } from 'app/course/shared/entities/course-available-tabs.model';

export type CourseHomeWelcomeCase =
    | 'BEFORE_START'
    | 'COURSE_ENDED'
    | 'EXAM_SOON'
    | 'DEADLINE_TODAY'
    | 'IMPORTANT_ANNOUNCEMENT'
    | 'UNREAD_REPLY'
    | 'FIRST_VISIT'
    | 'DEADLINE_SOON'
    | 'NEW_FEEDBACK'
    | 'RETURNING'
    | 'IN_PROGRESS'
    | 'NEW_EXERCISE'
    | 'NEW_LECTURE'
    | 'NEW_FAQ'
    | 'CAUGHT_UP';
export type CourseHomeSectionStatus = 'AVAILABLE' | 'DISABLED' | 'UNAVAILABLE';
export interface CourseHomeTarget {
    kind: 'EXERCISE' | 'LECTURE' | 'FAQ' | 'THREAD' | 'IRIS_SESSION' | 'EXAM' | 'STATISTICS' | 'CALENDAR' | 'LEARNING_PATH';
    id: number;
    conversationId?: number;
    focusReplyId?: number;
}
export interface CourseHomeItem {
    title: string;
    target: CourseHomeTarget;
    updatedAt?: string;
    dueAt?: string;
    reason: string;
}
export interface CourseHomeQuestion {
    postId: number;
    conversationId: number;
    title: string;
    totalReplies: number;
    state: 'AWAITING_REPLY' | 'ANSWERED' | 'RESOLVED';
    unreadReplies?: number;
    latestActivity: string;
    focusReplyId?: number;
}
export interface CourseHomeIrisSession {
    id: number;
    title?: string;
    mode: string;
    entityId: number;
    entityName?: string;
    lastActivity: string;
}
export interface CourseHomeTeachingTeam {
    items?: { id: number; name: string; imageUrl?: string; role: string }[];
    hasMore: boolean;
    page: number;
}
export interface CourseHomeGeneratedWelcome {
    contextFingerprint: string;
    message?: string;
    source: 'AI' | 'FALLBACK';
}
export interface CourseHome {
    asOf: string;
    visit: { firstVisit: boolean; previousVisitAt?: string; snapshotAt: string; variantCursor: number; newSince: string };
    course: { id: number; title: string; description?: string; language?: string; startDate?: string; endDate?: string; studentWelcomeContext?: string };
    capabilities: CourseAvailableTabs;
    progress: { releasedExercises: number; submittedExercises: number; currentlyDueExercises: number; currentlyDueSubmitted: number };
    welcome: { welcomeCase: CourseHomeWelcomeCase; variantIndex: number; params: Record<string, string>; contextFingerprint: string; aiEligible: boolean };
    nextStep?: CourseHomeItem;
    updates?: CourseHomeItem[];
    announcement?: CourseHomeItem;
    faqs?: CourseHomeItem[];
    teachingTeam: CourseHomeTeachingTeam;
    questions: { status: CourseHomeSectionStatus; items?: CourseHomeQuestion[] };
    irisSessions: { status: CourseHomeSectionStatus; items?: CourseHomeIrisSession[] };
}
