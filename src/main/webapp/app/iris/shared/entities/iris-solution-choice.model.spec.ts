import { describe, expect, it } from 'vitest';
import { IrisJsonMessageContent, IrisTextMessageContent } from './iris-content-type.model';
import { isClickedChoiceMessage, isSolutionOffer, offeredChoices, offersSummary, solutionChoiceForOffer } from './iris-solution-choice.model';
import { ChatServiceMode } from './iris-session-context.model';
import { IrisMessage, IrisSender } from './iris-message.model';

describe('Iris solution choice content', () => {
    it('recognizes a solution offer only in structured content', () => {
        expect(isSolutionOffer(new IrisJsonMessageContent({ type: 'solution-offer', chatMode: ChatServiceMode.PROGRAMMING_EXERCISE, entityId: 42 }))).toBe(true);
        expect(isSolutionOffer(new IrisJsonMessageContent({ type: 'mcq' }))).toBe(false);
        expect(isSolutionOffer(new IrisTextMessageContent('solution-offer'))).toBe(false);
    });

    it('restores the stored choice of a click or a typed reply and never infers one from text', () => {
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
        expect(solutionChoiceForOffer([choice(IrisSender.LLM, 10)], 10)).toBe('SHOW_SOLUTION');
        expect(solutionChoiceForOffer([{ sender: IrisSender.USER, content: [new IrisTextMessageContent('SHOW_SOLUTION')] }], 10)).toBeUndefined();
    });

    it('reads the buttons of an offer in order', () => {
        const offer = (choices?: string[]) => new IrisJsonMessageContent({ type: 'solution-offer', chatMode: ChatServiceMode.PROGRAMMING_EXERCISE, entityId: 42, choices });
        expect(offeredChoices(offer(['SUMMARIZE', 'SKIP_SUMMARY']))).toEqual(['SUMMARIZE', 'SKIP_SUMMARY']);
        expect(offeredChoices(offer())).toEqual([]);
        expect(offersSummary(offer(['SUMMARIZE', 'SKIP_SUMMARY']))).toBe(true);
        expect(offersSummary(offer(['TRY_MYSELF', 'SHOW_SOLUTION']))).toBe(false);
    });

    it('hides only the student message a click sends', () => {
        const choice = { type: 'solution-choice', offerMessageId: 10, choice: 'TRY_MYSELF' };
        const message = (sender: IrisSender, content: IrisMessage['content']) => ({ sender, content }) as IrisMessage;
        expect(isClickedChoiceMessage(message(IrisSender.USER, [new IrisTextMessageContent('Try it myself'), new IrisJsonMessageContent(choice)]))).toBe(true);
        expect(isClickedChoiceMessage(message(IrisSender.USER, [new IrisTextMessageContent('yes')]))).toBe(false);
        expect(isClickedChoiceMessage(message(IrisSender.LLM, [new IrisTextMessageContent('Here you go.'), new IrisJsonMessageContent(choice)]))).toBe(false);
    });
});
