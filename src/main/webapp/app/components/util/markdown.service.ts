import { Injectable, SecurityContext } from '@angular/core';
import * as showdown from 'showdown';
import { DomSanitizer } from '@angular/platform-browser';
import { MarkDownElement } from 'app/entities/quiz-question';
import { ExplanationCommand, HintCommand } from 'app/markdown-editor/domainCommands';
import { AceEditorComponent } from 'ng2-ace-editor';

@Injectable({ providedIn: 'root' })
export class ArtemisMarkdown {

    /**
     * adds the passed text into the editor of the passed ace editor component at the current curser by focusing, clearing a selection,
     * moving the cursor to the end of the line, and finally inserting the given text.
     * After that the new test will be selected
     *
     * @param text the text that will be added into the editor of the passed ace editor component
     * @param aceEditorContainer holds the editor in which the text will be added at the current curser position
     */
    static addTextAtCursor(text: String, aceEditorContainer: AceEditorComponent) {
        aceEditorContainer.getEditor().focus();
        aceEditorContainer.getEditor().clearSelection();
        aceEditorContainer.getEditor().moveCursorTo(aceEditorContainer.getEditor().getCursorPosition().row, Number.POSITIVE_INFINITY);
        aceEditorContainer.getEditor().insert(text);
        const range = aceEditorContainer.getEditor().selection.getRange();
        const identifier = text.split(']');
        const offset = identifier[0].length + 1;
        range.setStart(range.start.row, offset);
        aceEditorContainer.getEditor().selection.setRange(range);
    }

    constructor(private sanitizer: DomSanitizer) {}

    /**
     * Parse the markdown text and apply the result to the target object's data
     *
     * The markdown text is split at HintCommand.identifier and ExplanationCommand.identifier tags.
     *  => First part is text. Everything after HintCommand.identifier is Hint, anything after ExplanationCommand.identifier is explanation
     *
     * @param markdownText {string} the markdown text to parse
     * @param targetObject {object} the object that the result will be saved in. Fields modified are 'text', 'hint' and 'explanation'.
     */
    parseTextHintExplanation(markdownText: string, targetObject: MarkDownElement) {
        // split markdownText into main text, hint and explanation
        const markdownTextParts = markdownText.split(`/\\${ExplanationCommand.identifier}|\\${HintCommand.identifier}/g`);
        targetObject.text = markdownTextParts[0].trim();
        if (markdownText.indexOf(HintCommand.identifier) !== -1 && markdownText.indexOf(ExplanationCommand.identifier) !== -1) {
            if (markdownText.indexOf(HintCommand.identifier) < markdownText.indexOf(ExplanationCommand.identifier)) {
                targetObject.hint = markdownTextParts[1].trim();
                targetObject.explanation = markdownTextParts[2].trim();
            } else {
                targetObject.hint = markdownTextParts[2].trim();
                targetObject.explanation = markdownTextParts[1].trim();
            }
        } else if (markdownText.indexOf(HintCommand.identifier) !== -1) {
            targetObject.hint = markdownTextParts[1].trim();
            targetObject.explanation = null;
        } else if (markdownText.indexOf(ExplanationCommand.identifier) !== -1) {
            targetObject.hint = null;
            targetObject.explanation = markdownTextParts[1].trim();
        } else {
            targetObject.hint = null;
            targetObject.explanation = null;
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
    generateTextHintExplanation(sourceObject: MarkDownElement) {
        return (
            sourceObject.text +
            (sourceObject.hint ? '\n\t' + HintCommand.identifier + ' ' + sourceObject.hint : '') +
            (sourceObject.explanation ? '\n\t' + ExplanationCommand.identifier +  ' ' + sourceObject.explanation : '')
        );
    }

    /**
     * add the markdown for a hint at the current cursor location in the given editor
     * @deprecated NOTE: this method is DEPRECATED: please use the new markdown editor and appropriate domain commands
     * @param aceEditorContainer {object} the editor container into which the hint markdown will be inserted
     */
    addHintAtCursor(aceEditorContainer: AceEditorComponent) {
        const text = '\n\t' + HintCommand.identifier + HintCommand.text;
        ArtemisMarkdown.addTextAtCursor(text, aceEditorContainer);
    }

    /**
     * add the markdown for an explanation at the current cursor location in the given editor
     * @deprecated NOTE: this method is DEPRECATED: please use the new markdown editor and appropriate domain commands
     * @param aceEditorContainer {object} the editor container into which the explanation markdown will be inserted
     */
    // NOTE: this method is DEPRECATED: please use the new markdown editor and appropriate domain commands
    addExplanationAtCursor(aceEditorContainer: AceEditorComponent) {
        const text = '\n\t' + ExplanationCommand.identifier + ExplanationCommand.text;
        ArtemisMarkdown.addTextAtCursor(text, aceEditorContainer);
    }

    /**
     * converts markdown into html
     * @param {string} markdownText the original markdown text
     * @returns {string} the resulting html as a string
     */
    htmlForMarkdown(markdownText: string) {
        const converter = new showdown.Converter({
            parseImgDimensions: true,
            headerLevelStart: 3,
            simplifiedAutoLink: true,
            excludeTrailingPunctuationFromURLs: true,
            strikethrough: true,
            tables: true,
            openLinksInNewWindow: true,
            backslashEscapesHTMLTags: true
        });
        const html = converter.makeHtml(markdownText);
        return this.sanitizer.sanitize(SecurityContext.HTML, html);
    }
}
