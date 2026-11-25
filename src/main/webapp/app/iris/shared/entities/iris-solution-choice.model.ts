import { IrisJsonMessageContent, IrisMessageContent, isJsonContent } from './iris-content-type.model';
import { ChatServiceMode } from './iris-session-context.model';
import { IrisMessage, IrisSender } from './iris-message.model';

export type IrisSolutionChoice = 'TRY_MYSELF' | 'SHOW_SOLUTION';

export interface IrisSolutionChoiceRequest {
    offerMessageId: number;
    choice: IrisSolutionChoice;
}

export interface IrisSolutionOffer {
    type: 'solution-offer';
    chatMode: ChatServiceMode.PROGRAMMING_EXERCISE | ChatServiceMode.TEXT_EXERCISE;
    entityId: number;
}

interface IrisSolutionChoiceContent extends IrisSolutionChoiceRequest {
    type: 'solution-choice';
}

function isSolutionChoice(content: IrisMessageContent): content is IrisJsonMessageContent & { attributes: IrisSolutionChoiceContent } {
    if (!isJsonContent(content)) return false;
    const data = content.attributes;
    if (!data || typeof data !== 'object') return false;
    return (
        data['type'] === 'solution-choice' &&
        typeof data['offerMessageId'] === 'number' &&
        Number.isSafeInteger(data['offerMessageId']) &&
        data['offerMessageId'] > 0 &&
        (data['choice'] === 'TRY_MYSELF' || data['choice'] === 'SHOW_SOLUTION')
    );
}

/** A button action is kept in history for the workflow, without a student chat bubble. */
export function isSolutionChoiceMessage(message: IrisMessage): boolean {
    return message.sender === IrisSender.USER && message.content.some(isSolutionChoice);
}

export function isSolutionOffer(content: IrisMessageContent): content is IrisJsonMessageContent & { attributes: IrisSolutionOffer } {
    if (!isJsonContent(content)) return false;
    const data = content.attributes;
    if (!data || typeof data !== 'object') return false;
    return (
        data['type'] === 'solution-offer' &&
        (data['chatMode'] === ChatServiceMode.PROGRAMMING_EXERCISE || data['chatMode'] === ChatServiceMode.TEXT_EXERCISE) &&
        typeof data['entityId'] === 'number' &&
        Number.isSafeInteger(data['entityId']) &&
        data['entityId'] > 0
    );
}

/** Read the persisted selection, including after a history reload. */
export function solutionChoiceForOffer(messages: IrisMessage[], offerMessageId: number | undefined): IrisSolutionChoice | undefined {
    if (offerMessageId === undefined) return undefined;
    for (const message of messages) {
        if (message.sender !== IrisSender.USER) continue;
        for (const content of message.content) {
            if (isSolutionChoice(content) && content.attributes.offerMessageId === offerMessageId) {
                return content.attributes.choice;
            }
        }
    }
    return undefined;
}
