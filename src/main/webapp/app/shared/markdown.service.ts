import { Injectable } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import * as showdown from 'showdown';
import * as showdownKatex from 'showdown-katex';
import * as ace from 'brace';
import * as DOMPurify from 'dompurify';
import { AceEditorComponent } from 'ng2-ace-editor';
import { escapeStringForUseInRegex } from 'app/shared/util/global.utils';
import { ExplanationCommand } from 'app/shared/markdown-editor/domainCommands/explanation.command';
import { HintCommand } from 'app/shared/markdown-editor/domainCommands/hint.command';
import { TextHintExplanationInterface } from 'app/entities/quiz/quiz-question.model';

const Range = ace.acequire('ace/range').Range;

@Injectable({ providedIn: 'root' })
export class ArtemisMarkdown {
    static hintOrExpRegex = new RegExp(escapeStringForUseInRegex(`${ExplanationCommand.identifier}`) + '|' + escapeStringForUseInRegex(`${HintCommand.identifier}`), 'g');

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
        const commandIdentifier = text.split(']');
        const offsetRange = commandIdentifier[0].length + 1;
        range.setStart(range.start.row, offsetRange);
        aceEditorContainer.getEditor().selection.setRange(range);
    }

    /**
     * Remove the text at the specified range.
     * @param from = col & row from which to start
     * @param to = col & row at which to end
     * @param aceEditorContainer
     */
    static removeTextRange(from: { col: number; row: number }, to: { col: number; row: number }, aceEditorContainer: AceEditorComponent) {
        aceEditorContainer.getEditor().focus();
        aceEditorContainer
            .getEditor()
            .getSession()
            .remove(new Range(from.row, from.col, to.row, to.col));
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
    parseTextHintExplanation(markdownText: string, targetObject: TextHintExplanationInterface) {
        if (!markdownText || !targetObject) {
            return;
        }
        // split markdownText into main text, hint and explanation
        const markdownTextParts = markdownText.split(ArtemisMarkdown.hintOrExpRegex);
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
    generateTextHintExplanation(sourceObject: TextHintExplanationInterface) {
        return !sourceObject.text
            ? ''
            : sourceObject.text +
                  (sourceObject.hint ? '\n\t' + HintCommand.identifier + ' ' + sourceObject.hint : '') +
                  (sourceObject.explanation ? '\n\t' + ExplanationCommand.identifier + ' ' + sourceObject.explanation : '');
    }

    /**
     * Converts markdown into html, sanitizes it and then declares it as safe to bypass further security.
     *
     * @param {string} markdownText the original markdown text
     * @param {ShowdownExtension[]} extensions to use for markdown parsing
     * @returns {string} the resulting html as a SafeHtml object that can be inserted into the angular template
     */
    safeHtmlForMarkdown(markdownText: string | null, extensions: showdown.ShowdownExtension[] = []): SafeHtml {
        if (markdownText == null || markdownText === '') {
            return '';
        }
        const convertedString = this.htmlForMarkdown(markdownText, extensions);
        return this.sanitizer.bypassSecurityTrustHtml(convertedString);
    }

    /**
     * Converts markdown into html (string) and sanitizes it. Does NOT declare it as safe to bypass further security
     * Note: If possible, please use safeHtmlForMarkdown
     *
     * @param {string} markdownText the original markdown text
     * @param {ShowdownExtension[]} extensions to use for markdown parsing
     * @returns {string} the resulting html as a SafeHtml object that can be inserted into the angular template
     */
    htmlForMarkdown(markdownText: string | null, extensions: showdown.ShowdownExtension[] = []): string {
        if (markdownText == null || markdownText === '') {
            return '';
        }
        const converter = new showdown.Converter({
            parseImgDimensions: true,
            headerLevelStart: 3,
            simplifiedAutoLink: true,
            excludeTrailingPunctuationFromURLs: true,
            strikethrough: true,
            tables: true,
            openLinksInNewWindow: true,
            backslashEscapesHTMLTags: true,
            extensions: [...extensions, showdownKatex()],
        });
        const html = converter.makeHtml(markdownText);
        return DOMPurify.sanitize(html);
    }

    /**
     * Converts markdown into html, sanitizes it and then declares it as safe to bypass further security.
     *
     * @param {string} markdownText the original markdown text
     * @returns {string} the resulting html as a string
     */
    htmlForGuidedTourMarkdown(markdownText: string | null): SafeHtml {
        if (markdownText == null || markdownText === '') {
            return '';
        }
        const sanitized = DOMPurify.sanitize(markdownText, { ALLOWED_TAGS: ['a', 'p', 'ul', 'ol', 'li', 'tt', 'span'], ALLOWED_ATTR: ['class', 'href', 'rel', 'target'] });
        return this.sanitizer.bypassSecurityTrustHtml(sanitized);
    }

    markdownForHtml(htmlText: string): string {
        const converter = new showdown.Converter({
            parseImgDimensions: true,
            headerLevelStart: 3,
            simplifiedAutoLink: true,
            excludeTrailingPunctuationFromURLs: true,
            strikethrough: true,
            tables: true,
            openLinksInNewWindow: true,
            backslashEscapesHTMLTags: true,
        });
        return converter.makeMarkdown(htmlText);
    }
}
