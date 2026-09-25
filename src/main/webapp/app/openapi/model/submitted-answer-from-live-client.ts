import type { EntityIdRef } from './entity-id-ref';
import type { DragAndDropMappingFromLiveClient } from './drag-and-drop-mapping-from-live-client';
import type { ShortAnswerSubmittedTextFromLiveClient } from './short-answer-submitted-text-from-live-client';

export interface SubmittedAnswerFromLiveClient {
    type: string;
    quizQuestion?: EntityIdRef;
    selectedOptions?: Array<EntityIdRef>;
    mappings?: Array<DragAndDropMappingFromLiveClient>;
    submittedTexts?: Array<ShortAnswerSubmittedTextFromLiveClient>;
}

