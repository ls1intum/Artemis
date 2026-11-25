import { IrisJsonMessageContent, IrisMessageContent, isJsonContent } from './iris-content-type.model';
import { IrisMessage, IrisSender } from './iris-message.model';

export type IrisSolutionChoice = 'TRY_MYSELF' | 'SHOW_SOLUTION' | 'SUMMARIZE' | 'SKIP_SUMMARY';

/** The label of each choice, under artemisApp.iris.solutionChoice; it is also the text of the student message a click sends. */
export const SOLUTION_CHOICE_LABEL_KEYS: Record<IrisSolutionChoice, string> = {
    TRY_MYSELF: 'artemisApp.iris.solutionChoice.tryMyself',
    SHOW_SOLUTION: 'artemisApp.iris.solutionChoice.showSolution',
    SUMMARIZE: 'artemisApp.iris.solutionChoice.summarize',
    SKIP_SUMMARY: 'artemisApp.iris.solutionChoice.skipSummary',
};

export interface IrisSolutionChoiceRequest {
    offerMessageId: number;
    choice: IrisSolutionChoice;
}

/** The structured part of an Iris answer that offers the choice; the server validates its binding when a choice arrives. */
export function isSolutionOffer(content: IrisMessageContent): content is IrisJsonMessageContent {
    return isJsonContent(content) && content.attributes?.['type'] === 'solution-offer';
}

/** Whether the offer invites the student to summarize a delivered solution; the client shows its fixed invitation above the buttons. */
export function offersSummary(offer: IrisJsonMessageContent): boolean {
    return offeredChoices(offer).includes('SUMMARIZE');
}

/** The buttons of an offer, in order. */
export function offeredChoices(offer: IrisJsonMessageContent): IrisSolutionChoice[] {
    return (offer.attributes?.['choices'] as IrisSolutionChoice[] | undefined) ?? [];
}

/** The choice the student made for an offer, by click or typed reply, also after a history reload; only the server writes choices. */
export function solutionChoiceForOffer(messages: IrisMessage[], offerMessageId: number | undefined): IrisSolutionChoice | undefined {
    if (offerMessageId === undefined) return undefined;
    for (const message of messages) {
        for (const content of message.content) {
            const data = isJsonContent(content) ? content.attributes : undefined;
            if (data?.['type'] === 'solution-choice' && data['offerMessageId'] === offerMessageId) {
                return data['choice'] as IrisSolutionChoice;
            }
        }
    }
    return undefined;
}

/** The student message a click sends: it records the choice for the server and Iris, while the chat shows the choice only as the pressed button. */
export function isClickedChoiceMessage(message: IrisMessage): boolean {
    return message.sender === IrisSender.USER && message.content.some((content) => isJsonContent(content) && content.attributes?.['type'] === 'solution-choice');
}
