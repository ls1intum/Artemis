import type { MultipleChoiceSubmittedAnswerFromLiveClient } from "./multiple-choice-submitted-answer-from-live-client";
import type { DragAndDropSubmittedAnswerFromLiveClient } from "./drag-and-drop-submitted-answer-from-live-client";
import type { ShortAnswerSubmittedAnswerFromLiveClient } from "./short-answer-submitted-answer-from-live-client";

export type SubmittedAnswerFromLiveClient = MultipleChoiceSubmittedAnswerFromLiveClient | DragAndDropSubmittedAnswerFromLiveClient | ShortAnswerSubmittedAnswerFromLiveClient;
