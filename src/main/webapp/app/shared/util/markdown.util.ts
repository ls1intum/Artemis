import { ExerciseHintExplanationInterface } from 'app/entities/quiz/quiz-question.model';
import { hintCommentIdentifier } from 'app/shared/markdown-editor/domainCommands/hint.command';
import { explanationCommandIdentifier } from 'app/shared/markdown-editor/domainCommands/explanation.command';
import { escapeStringForUseInRegex } from 'app/shared/util/global.utils';

const hintOrExpRegex = new RegExp(escapeStringForUseInRegex(`${explanationCommandIdentifier}`) + '|' + escapeStringForUseInRegex(`${hintCommentIdentifier}`), 'g');

/**
 * adds the passed text into the editor of the passed ace editor component at the current curser by focusing, clearing a selection,
 * moving the cursor to the end of the line, and finally inserting the given text.
 * After that the new test will be selected
 *
 * @param text the text that will be added into the editor of the passed ace editor component
 * @param aceEditor the editor in which the text will be added at the current curser position
 */
export function addTextAtCursor(text: string, aceEditor: any) {
    aceEditor.focus();
    aceEditor.clearSelection();
    aceEditor.moveCursorTo(aceEditor.getCursorPosition().row, Number.POSITIVE_INFINITY);
    aceEditor.insert(text);
    const range = aceEditor.selection.getRange();
    const commandIdentifier = text.split(']');
    const offsetRange = commandIdentifier[0].length + 1;
    range.setStart(range.start.row, offsetRange);
    aceEditor.selection.setRange(range);
}

/**
 * Remove the text at the specified range.
 * @param from = col & row from which to start
 * @param to = col & row at which to end
 * @param aceEditor the editor in which text should be removed
 */
export function removeTextRange(from: { col: number; row: number }, to: { col: number; row: number }, aceEditor: any) {
    aceEditor.focus();
    aceEditor.getSession().remove({ startRow: from.row, startColumn: from.col, endRow: to.row, endColumn: to.col });
}

/**
 * Parse the markdown text and apply the result to the target object's data
 *
 * The markdown text is split at hintCommentIdentifier and explanationCommandIdentifier tags.
 *  => First part is text. Everything after hintCommentIdentifier is Hint, anything after explanationCommandIdentifier is explanation
 *
 * @param markdownText {string} the markdown text to parse
 * @param targetObject {object} the object that the result will be saved in. Fields modified are 'text', 'hint' and 'explanation'.
 */
export function parseExerciseHintExplanation(markdownText: string, targetObject: ExerciseHintExplanationInterface) {
    if (!markdownText || !targetObject) {
        return;
    }
    // split markdownText into main text, hint and explanation
    const markdownTextParts = markdownText.split(hintOrExpRegex);
    targetObject.text = markdownTextParts[0].trim();
    if (markdownText.indexOf(hintCommentIdentifier) !== -1 && markdownText.indexOf(explanationCommandIdentifier) !== -1) {
        if (markdownText.indexOf(hintCommentIdentifier) < markdownText.indexOf(explanationCommandIdentifier)) {
            targetObject.hint = markdownTextParts[1].trim();
            targetObject.explanation = markdownTextParts[2].trim();
        } else {
            targetObject.hint = markdownTextParts[2].trim();
            targetObject.explanation = markdownTextParts[1].trim();
        }
    } else if (markdownText.indexOf(hintCommentIdentifier) !== -1) {
        targetObject.hint = markdownTextParts[1].trim();
        targetObject.explanation = undefined;
    } else if (markdownText.indexOf(explanationCommandIdentifier) !== -1) {
        targetObject.hint = undefined;
        targetObject.explanation = markdownTextParts[1].trim();
    } else {
        targetObject.hint = undefined;
        targetObject.explanation = undefined;
    }
}

/**
 * generate the markdown text for the given source object
 *
 * The markdown is generated according to these rules:
 *
 * 1. First the value of sourceObject.text is inserted
 * 2. If hint and/or explanation exist, they are added after the text with a linebreak and tab in front of them
 * 3. Hint starts with [-h], explanation starts with [-e]
 *
 * @param sourceObject
 * @return {string}
 */
export function generateExerciseHintExplanation(sourceObject: ExerciseHintExplanationInterface) {
    return !sourceObject.text
        ? ''
        : sourceObject.text +
              (sourceObject.hint ? '\n\t' + hintCommentIdentifier + ' ' + sourceObject.hint : '') +
              (sourceObject.explanation ? '\n\t' + explanationCommandIdentifier + ' ' + sourceObject.explanation : '');
}
