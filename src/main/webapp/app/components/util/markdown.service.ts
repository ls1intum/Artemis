import { Injectable, SecurityContext } from '@angular/core';
import * as showdown from 'showdown';
import { DomSanitizer } from '@angular/platform-browser';

@Injectable()
export class ArtemisMarkdown {

    constructor(private sanitizer: DomSanitizer) { }

    /**
     * Parse the markdown text and apply the result to the target object's data
     *
     * The markdown text is split at [-h] and [-e] tags.
     *  => First part is text. Everything after [-h] is Hint, anything after [-e] is explanation
     *
     * @param markdownText {string} the markdown text to parse
     * @param targetObject {object} the object that the result will be saved in. Fields modified are 'text', 'hint' and 'explanation'.
     */
    parseTextHintExplanation(markdownText, targetObject) {
        // split markdownText into main text, hint and explanation
        const markdownTextParts = markdownText.split(/\[-e]|\[-h]/g);
        targetObject.text = markdownTextParts[0].trim();
        if (markdownText.indexOf('[-h]') !== -1 && markdownText.indexOf('[-e]') !== -1) {
            if (markdownText.indexOf('[-h]') < markdownText.indexOf('[-e]')) {
                targetObject.hint = markdownTextParts[1].trim();
                targetObject.explanation = markdownTextParts[2].trim();
            } else {
                targetObject.hint = markdownTextParts[2].trim();
                targetObject.explanation = markdownTextParts[1].trim();
            }
        } else if (markdownText.indexOf('[-h]') !== -1) {
            targetObject.hint = markdownTextParts[1].trim();
            targetObject.explanation = null;
        } else if (markdownText.indexOf('[-e]') !== -1) {
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
    generateTextHintExplanation(sourceObject) {
        return (
            sourceObject.text +
            (sourceObject.hint ? '\n\t[-h] ' + sourceObject.hint : '') +
            (sourceObject.explanation ? '\n\t[-e] ' + sourceObject.explanation : '')
        );
    }

    /**
     * add the markdown for a hint at the current cursor location in the given editor
     *
     * @param editor {object} the editor into which the hint markdown will be inserted
     */
    addHintAtCursor(editor) {
        const addedText = '\n\t[-h] Add a hint here (visible during the quiz via \'?\'-Button)';
        editor.focus();
        editor.clearSelection();
        editor.moveCursorTo(editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        editor.insert(addedText);
        const range = editor.selection.getRange();
        range.setStart(range.start.row, 6);
        editor.selection.setRange(range);
    }

    /**
     * add the markdown for an explanation at the current cursor location in the given editor
     *
     * @param editor {object} the editor into which the explanation markdown will be inserted
     */
    addExplanationAtCursor(editor) {
        const addedText = '\n\t[-e] Add an explanation here (only visible in feedback after quiz has ended)';
        editor.focus();
        editor.clearSelection();
        editor.moveCursorTo(editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        editor.insert(addedText);
        const range = editor.selection.getRange();
        range.setStart(range.start.row, 6);
        editor.selection.setRange(range);
    }

    /**
     * converts markdown into html
     * @param {string} markdownText the original markdown text
     * @returns {string} the resulting html as a string
     */
    htmlForMarkdown(markdownText) {
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
