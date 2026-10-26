import { CourseHome, CourseHomeWelcomeCase } from '../course-home.model';

export const COURSE_HOME_SITUATIONS = [
    'before',
    'first',
    'new-exercise',
    'due-today',
    'unread-replies',
    'waiting',
    'new-content',
    'feedback',
    'returning',
    'iris-follow-up',
    'caught-up',
    'exam',
    'finished',
    'finished-partial',
    'ai-outage',
    'minimal',
] as const;
export type CourseHomeSituation = (typeof COURSE_HOME_SITUATIONS)[number];
export function createCourseHomeFixture(situation: CourseHomeSituation): CourseHome {
    const cases: Record<CourseHomeSituation, CourseHomeWelcomeCase> = {
        before: 'BEFORE_START',
        first: 'FIRST_VISIT',
        'new-exercise': 'NEW_EXERCISE',
        'due-today': 'DEADLINE_TODAY',
        'unread-replies': 'UNREAD_REPLY',
        waiting: 'IN_PROGRESS',
        'new-content': 'IMPORTANT_ANNOUNCEMENT',
        feedback: 'NEW_FEEDBACK',
        returning: 'RETURNING',
        'iris-follow-up': 'CAUGHT_UP',
        'caught-up': 'CAUGHT_UP',
        exam: 'EXAM_SOON',
        finished: 'COURSE_ENDED',
        'finished-partial': 'COURSE_ENDED',
        'ai-outage': 'RETURNING',
        minimal: 'IN_PROGRESS',
    };
    const minimal = situation === 'minimal';
    const first = situation === 'before' || situation === 'first';
    const complete = situation === 'finished' || situation === 'caught-up' || situation === 'exam';
    const home: CourseHome = {
        asOf: '2026-10-26T10:00:00Z',
        sections: { lectures: 'AVAILABLE', exams: 'AVAILABLE', faq: 'AVAILABLE' },
        visit: { firstVisit: first, snapshotAt: '2026-10-26T10:00:00Z', variantCursor: 2, newSince: '2026-10-19T10:00:00Z' },
        course: {
            id: 42,
            title: 'Software Engineering',
            studentWelcomeContext: 'Learn by building, reflecting and using feedback.',
            startDate: '2026-10-19T08:00:00Z',
            endDate: '2027-02-12T18:00:00Z',
        },
        capabilities: {
            lectures: true,
            exams: true,
            faq: true,
            communication: !minimal,
            iris: !minimal,
            competencies: false,
            tutorialGroups: false,
            learningPaths: false,
            training: false,
        },
        progress: {
            releasedExercises: situation === 'before' ? 0 : 8,
            submittedExercises: complete ? 8 : first ? 0 : 6,
            currentlyDueExercises: first ? 0 : 2,
            currentlyDueSubmitted: complete ? 2 : 1,
        },
        welcome: { welcomeCase: cases[situation], variantIndex: 2, params: {}, contextFingerprint: situation, aiEligible: !minimal && situation !== 'before' },
        nextStep: first ? undefined : { title: 'Object Modeling', target: { kind: 'EXERCISE', id: 17 }, reason: cases[situation] },
        teachingTeam: {
            items: [
                { id: 1, name: 'Ada Lovelace', role: 'INSTRUCTOR' },
                { id: 2, name: 'Alan Turing', role: 'TEACHING_ASSISTANT' },
            ],
            hasMore: true,
            page: 0,
        },
        updates: [{ title: 'Design patterns', target: { kind: 'LECTURE', id: 7 }, reason: 'NEW_LECTURE', updatedAt: '2026-10-25T09:00:00Z' }],
        faqs: [{ title: 'How do I submit my work?', target: { kind: 'FAQ', id: 12 }, reason: 'NEW_FAQ' }],
        questions: {
            status: minimal ? 'DISABLED' : 'AVAILABLE',
            items:
                first || minimal
                    ? []
                    : [
                          {
                              postId: 21,
                              conversationId: 8,
                              title: 'Can I use more than one design pattern?',
                              totalReplies: situation === 'waiting' ? 0 : 2,
                              state: situation === 'waiting' ? 'AWAITING_REPLY' : 'ANSWERED',
                              unreadReplies: situation === 'unread-replies' ? 2 : 0,
                              focusReplyId: 23,
                              latestActivity: '2026-10-26T09:00:00Z',
                          },
                      ],
        },
        irisSessions: {
            status: minimal ? 'DISABLED' : 'AVAILABLE',
            items: first || minimal ? [] : [{ id: 31, title: 'Understanding coupling', mode: 'COURSE', entityId: 42, lastActivity: '2026-10-25T09:00:00Z' }],
        },
    };
    if (situation === 'before') home.course.startDate = '2026-11-02T08:00:00Z';
    if (!first) home.visit.previousVisitAt = situation === 'returning' ? '2026-10-01T10:00:00Z' : '2026-10-25T10:00:00Z';
    if (situation === 'unread-replies')
        home.nextStep = { title: home.questions.items![0].title, target: { kind: 'THREAD', id: 21, conversationId: 8, focusReplyId: 23 }, reason: 'UNREAD_REPLY' };
    if (situation === 'new-content') home.nextStep = { title: 'Project presentations', target: { kind: 'THREAD', id: 29, conversationId: 8 }, reason: 'IMPORTANT_ANNOUNCEMENT' };
    if (situation === 'caught-up') home.nextStep = { title: 'Understanding coupling', target: { kind: 'IRIS_SESSION', id: 31 }, reason: 'CAUGHT_UP' };
    if (situation === 'finished' || situation === 'finished-partial') home.course.endDate = '2026-10-23T18:00:00Z';
    if (situation === 'exam') home.nextStep = { title: 'Final exam', target: { kind: 'EXAM', id: 55 }, reason: 'EXAM_SOON', dueAt: '2026-10-27T09:00:00Z' };
    if (situation === 'new-content')
        home.announcement = { title: 'Project presentations', target: { kind: 'THREAD', id: 29, conversationId: 8 }, reason: 'IMPORTANT_ANNOUNCEMENT' };
    if (situation === 'iris-follow-up') home.nextStep = { title: 'Continue with Iris', target: { kind: 'IRIS_SESSION', id: 31 }, reason: 'IRIS_SESSION' };
    if (situation === 'due-today') home.nextStep!.dueAt = '2026-10-26T18:00:00Z';
    if (situation === 'finished' || situation === 'finished-partial') home.nextStep = { title: 'Review course material', target: { kind: 'LECTURE', id: 7 }, reason: 'REVIEW' };
    return home;
}
