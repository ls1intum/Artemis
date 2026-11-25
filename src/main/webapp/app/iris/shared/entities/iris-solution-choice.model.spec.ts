import { describe, expect, it } from 'vitest';
import { IrisJsonMessageContent, IrisTextMessageContent } from './iris-content-type.model';
import { isSolutionOffer, solutionChoiceForOffer } from './iris-solution-choice.model';
import { ChatServiceMode } from './iris-session-context.model';
import { IrisMessage, IrisSender } from './iris-message.model';

describe('Iris solution choice content', () => {
    it('recognizes a solution offer only in structured content', () => {
        expect(isSolutionOffer(new IrisJsonMessageContent({ type: 'solution-offer', chatMode: ChatServiceMode.PROGRAMMING_EXERCISE, entityId: 42 }))).toBe(true);
        expect(isSolutionOffer(new IrisJsonMessageContent({ type: 'mcq' }))).toBe(false);
        expect(isSolutionOffer(new IrisTextMessageContent('solution-offer'))).toBe(false);
    });

    it('restores the stored choice of a student message and never infers one from text or from Iris', () => {
        const choice = (sender: IrisSender, offerMessageId: number) =>
            ({
                sender,
                content: [
                    new IrisTextMessageContent('Show me the complete solution'),
                    new IrisJsonMessageContent({ type: 'solution-choice', offerMessageId, choice: 'SHOW_SOLUTION' }),
                ],
            }) as IrisMessage;
        expect(solutionChoiceForOffer([choice(IrisSender.USER, 10)], 10)).toBe('SHOW_SOLUTION');
        expect(solutionChoiceForOffer([choice(IrisSender.USER, 10)], 9)).toBeUndefined();
        expect(solutionChoiceForOffer([choice(IrisSender.USER, 10)], undefined)).toBeUndefined();
        expect(solutionChoiceForOffer([choice(IrisSender.LLM, 10)], 10)).toBeUndefined();
        expect(solutionChoiceForOffer([{ sender: IrisSender.USER, content: [new IrisTextMessageContent('SHOW_SOLUTION')] }], 10)).toBeUndefined();
    });
});
