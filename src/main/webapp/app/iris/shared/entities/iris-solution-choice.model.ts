import { IrisJsonMessageContent, IrisMessageContent, isJsonContent } from './iris-content-type.model';
import { IrisMessage, IrisSender } from './iris-message.model';

export type IrisSolutionChoice = 'TRY_MYSELF' | 'SHOW_SOLUTION';

export interface IrisSolutionChoiceRequest {
    offerMessageId: number;
    choice: IrisSolutionChoice;
}

/** The structured part of an Iris answer that offers the choice; the server validates its binding when a choice arrives. */
export function isSolutionOffer(content: IrisMessageContent): content is IrisJsonMessageContent {
    return isJsonContent(content) && content.attributes?.['type'] === 'solution-offer';
}

/** The choice the student made for an offer, also after a history reload; only the server writes choices. */
export function solutionChoiceForOffer(messages: IrisMessage[], offerMessageId: number | undefined): IrisSolutionChoice | undefined {
    if (offerMessageId === undefined) return undefined;
    for (const message of messages) {
        if (message.sender !== IrisSender.USER) continue;
        for (const content of message.content) {
            const data = isJsonContent(content) ? content.attributes : undefined;
            if (data?.['type'] === 'solution-choice' && data['offerMessageId'] === offerMessageId) {
                return data['choice'] as IrisSolutionChoice;
            }
        }
    }
    return undefined;
}
