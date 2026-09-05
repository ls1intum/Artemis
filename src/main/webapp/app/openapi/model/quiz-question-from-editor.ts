import type { ShortAnswerQuestionFromEditor } from './short-answer-question-from-editor';
import type { DragAndDropQuestionFromEditor } from './drag-and-drop-question-from-editor';
import type { MultipleChoiceQuestionFromEditor } from './multiple-choice-question-from-editor';

export type QuizQuestionFromEditor = MultipleChoiceQuestionFromEditor | DragAndDropQuestionFromEditor | ShortAnswerQuestionFromEditor;
