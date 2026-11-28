import { describe, expect, it } from 'vitest';
import { IrisActivityKind, IrisActivityState } from 'app/iris/shared/entities/iris-activity.model';
import { mapChatMessage, mapChatSession } from './iris-chat-http.mapper';

describe('Iris chat HTTP mapper', () => {
    it('keeps absent session mode, entity and messages optional', () => {
        const dto = { id: 1, userId: 2, creationDate: '2026-09-20T12:00:00Z' };
        expect(mapChatSession(dto)).toMatchObject({ ...dto, mode: undefined, entityId: undefined, messages: undefined });
    });

    it('normalizes omitted content and retains origin, activity and citation metadata', () => {
        const citation = { entityId: 1, lectureTitle: 'Intro', lectureUnitTitle: 'Slides', lectureId: 2, courseId: 3 };
        const activity = { id: '1', kind: IrisActivityKind.TOOL, name: 'search', state: IrisActivityState.FINISHED, durationMillis: 0 };
        const message = {
            sender: 'LLM',
            final: false,
            origin: 'PROACTIVE_STRUGGLE',
            proactiveOutcome: 'RECOVERED',
            proactiveEpisodeId: 'episode',
            activities: [activity],
        } as const;
        const mapped = mapChatSession({
            id: 1,
            userId: 2,
            creationDate: '2026-09-20T12:00:00Z',
            citationInfo: [citation],
            messages: [{ ...message, activities: [activity] }],
        });

        expect(mapped.citationInfo).toEqual([citation]);
        expect(mapped.messages).toMatchObject([{ ...message, content: [] }]);
    });

    it('preserves raw JSON attributes on content', () => {
        const content = [{ type: 'json', attributes: { selectedIndex: 0, submitted: false } }];
        expect(mapChatMessage({ sender: 'LLM', content }).content).toEqual(content);
    });
});
