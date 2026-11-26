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

/** The structured part of a student message that records a choice; only the server writes it. */
function isSolutionChoice(content: IrisMessageContent): content is IrisJsonMessageContent {
    return isJsonContent(content) && content.attributes?.['type'] === 'solution-choice';
}

/** Whether the offer invites the student to summarize a delivered solution; the client shows its fixed invitation above the buttons. */
export function offersSummary(offer: IrisJsonMessageContent): boolean {
    return offeredChoices(offer).includes('SUMMARIZE');
}

/** The buttons of an offer, in order. */
export function offeredChoices(offer: IrisJsonMessageContent): IrisSolutionChoice[] {
    return (offer.attributes?.['choices'] as IrisSolutionChoice[] | undefined) ?? [];
}

/** The choice the student made for each offer, by click or typed reply, keyed by the offer's message id; only the server writes choices. */
export function solutionChoicesByOffer(messages: IrisMessage[]): Map<number, IrisSolutionChoice> {
    const choices = new Map<number, IrisSolutionChoice>();
    for (const content of messages.flatMap((message) => message.content).filter(isSolutionChoice)) {
        const offerMessageId = content.attributes?.['offerMessageId'] as number;
        if (!choices.has(offerMessageId)) choices.set(offerMessageId, content.attributes?.['choice'] as IrisSolutionChoice);
    }
    return choices;
}

/** The student message a click sends: it records the choice for the server and Iris, while the chat shows the choice only as the pressed button. */
export function isClickedChoiceMessage(message: IrisMessage): boolean {
    return message.sender === IrisSender.USER && message.content.some(isSolutionChoice);
}
