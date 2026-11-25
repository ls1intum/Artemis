import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs/esm';
import { IrisJsonMessageContent, IrisTextMessageContent } from './iris-content-type.model';
import { isSolutionChoiceMessage, isSolutionOffer, solutionChoiceForOffer } from './iris-solution-choice.model';
import { ChatServiceMode } from './iris-session-context.model';
import { IrisMessage, IrisSender } from './iris-message.model';

describe('Iris solution choice content', () => {
    it('recognizes only offers with a valid context binding', () => {
        expect(isSolutionOffer(new IrisJsonMessageContent({ type: 'solution-offer', chatMode: ChatServiceMode.PROGRAMMING_EXERCISE, entityId: 42 }))).toBe(true);
        expect(isSolutionOffer(new IrisTextMessageContent('solution-offer'))).toBe(false);
        expect(isSolutionOffer(new IrisJsonMessageContent({ type: 'solution-offer', chatMode: 'UNKNOWN', entityId: 42 }))).toBe(false);
        expect(isSolutionOffer(new IrisJsonMessageContent({ type: 'solution-offer', chatMode: ChatServiceMode.PROGRAMMING_EXERCISE, entityId: -1 }))).toBe(false);
    });

    it('supports text exercise offers', () => {
        expect(isSolutionOffer(new IrisJsonMessageContent({ type: 'solution-offer', chatMode: ChatServiceMode.TEXT_EXERCISE, entityId: 42 }))).toBe(true);
    });

    it.each([ChatServiceMode.COURSE, ChatServiceMode.LECTURE, ChatServiceMode.TUTOR_SUGGESTION])('ignores solution offers outside exercise chats (%s)', (chatMode) => {
        expect(isSolutionOffer(new IrisJsonMessageContent({ type: 'solution-offer', chatMode, entityId: 42 }))).toBe(false);
    });

    it('restores an explicit persisted choice and does not infer one from text', () => {
        const persisted = {
            id: 11,
            sender: IrisSender.USER,
            content: [new IrisTextMessageContent('Show me the solution'), new IrisJsonMessageContent({ type: 'solution-choice', offerMessageId: 10, choice: 'SHOW_SOLUTION' })],
        } as IrisMessage;
        expect(solutionChoiceForOffer([persisted], 10)).toBe('SHOW_SOLUTION');
        expect(solutionChoiceForOffer([persisted], 9)).toBeUndefined();
        expect(solutionChoiceForOffer([persisted], undefined)).toBeUndefined();
        expect(solutionChoiceForOffer([{ sender: IrisSender.USER, content: [new IrisTextMessageContent('SHOW_SOLUTION')] }], 10)).toBeUndefined();
    });

    it('ignores choice-shaped assistant content', () => {
        const message: IrisMessage = {
            id: 11,
            sender: IrisSender.LLM,
            content: [new IrisJsonMessageContent({ type: 'solution-choice', offerMessageId: 10, choice: 'SHOW_SOLUTION' })],
            sentAt: dayjs(0),
        };
        expect(solutionChoiceForOffer([message], 10)).toBeUndefined();
        expect(isSolutionChoiceMessage(message)).toBe(false);
    });

    it.each(['TRY_MYSELF', 'SHOW_SOLUTION'])('recognizes the internal %s action without inferring it from typed text', (choice) => {
        const action: IrisMessage = {
            sender: IrisSender.USER,
            content: [new IrisTextMessageContent('Button label'), new IrisJsonMessageContent({ type: 'solution-choice', offerMessageId: 10, choice })],
        };
        expect(isSolutionChoiceMessage(action)).toBe(true);
        expect(isSolutionChoiceMessage({ sender: IrisSender.USER, content: [new IrisTextMessageContent('Show me the complete solution.')] })).toBe(false);
    });

    it.each([0, -1, 1.5, '10', undefined])('does not hide malformed choices with offer id %s', (offerMessageId) => {
        const action: IrisMessage = {
            sender: IrisSender.USER,
            content: [new IrisJsonMessageContent({ type: 'solution-choice', offerMessageId, choice: 'SHOW_SOLUTION' })],
        };
        expect(isSolutionChoiceMessage(action)).toBe(false);
        expect(solutionChoiceForOffer([action], 10)).toBeUndefined();
    });
});
