import { courseHomeTarget } from './course-home-target.util';

describe('course Home destinations', () => {
    it.each(['EXERCISE', 'LECTURE', 'EXAM', 'STATISTICS', 'CALENDAR', 'LEARNING_PATH'] as const)('links %s to its existing page', (kind) => {
        const paths = { EXERCISE: 'exercises', LECTURE: 'lectures', EXAM: 'exams', STATISTICS: 'statistics', CALENDAR: 'calendar', LEARNING_PATH: 'learning-path' };
        const link = courseHomeTarget(42, { kind, id: 17 });
        expect(link.commands).toEqual(kind === 'EXERCISE' || kind === 'LECTURE' || kind === 'EXAM' ? ['/courses', 42, paths[kind], 17] : ['/courses', 42, paths[kind]]);
    });
    it('focuses the original question and unread reply', () => {
        expect(courseHomeTarget(42, { kind: 'THREAD', id: 17, conversationId: 8, focusReplyId: 19 })).toEqual({
            commands: ['/courses', 42, 'communication'],
            queryParams: { conversationId: 8, messageId: 17, focusReplyId: 19 },
        });
    });
    it('selects the FAQ and existing Iris session', () => {
        expect(courseHomeTarget(42, { kind: 'FAQ', id: 17 }).queryParams).toEqual({ faqId: 17 });
        expect(courseHomeTarget(42, { kind: 'IRIS_SESSION', id: 19 }).queryParams).toEqual({ irisSession: 19 });
    });
});
