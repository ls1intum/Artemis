import { Injectable } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import showdown from 'showdown';
import showdownKatex from 'showdown-katex';
import showdownHighlight from 'showdown-highlight';
import DOMPurify from 'dompurify';
import { escapeStringForUseInRegex } from 'app/shared/util/global.utils';
import { ExplanationCommand } from 'app/shared/markdown-editor/domainCommands/explanation.command';
import { HintCommand } from 'app/shared/markdown-editor/domainCommands/hint.command';
import { TextHintExplanationInterface } from 'app/entities/quiz/quiz-question.model';

/**
 * showdown will add the classes to the converted html
 * see: https://github.com/showdownjs/showdown/wiki/Add-default-classes-for-each-HTML-element
 */
const classMap = {
    table: 'table',
};
/**
 * extension to add add css classes to html tags
 * see: https://github.com/showdownjs/showdown/wiki/Add-default-classes-for-each-HTML-element
 */
const addCSSClass = Object.keys(classMap).map((key) => ({
    type: 'output',
    regex: new RegExp(`<${key}(.*)>`, 'g'),
    replace: `<${key} class="${classMap[key]}" $1>`,
}));

@Injectable({ providedIn: 'root' })
export class ArtemisMarkdownService {
    static hintOrExpRegex = new RegExp(escapeStringForUseInRegex(`${ExplanationCommand.identifier}`) + '|' + escapeStringForUseInRegex(`${HintCommand.identifier}`), 'g');

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
    static parseTextHintExplanation(markdownText: string, targetObject: TextHintExplanationInterface) {
        if (!markdownText || !targetObject) {
            return;
        }
        // split markdownText into main text, hint and explanation
        const markdownTextParts = markdownText.split(ArtemisMarkdownService.hintOrExpRegex);
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
            targetObject.explanation = undefined;
        } else if (markdownText.indexOf(ExplanationCommand.identifier) !== -1) {
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
     * @param {showdown.ShowdownExtension[]} extensions to use for markdown parsing
     * @param {string[]} allowedHtmlTags to allow during sanitization
     * @param {string[]} allowedHtmlAttributes to allow during sanitization
     * @returns {string} the resulting html as a SafeHtml object that can be inserted into the angular template
     */
    safeHtmlForMarkdown(
        markdownText?: string,
        extensions: showdown.ShowdownExtension[] = [],
        allowedHtmlTags: string[] | undefined = undefined,
        allowedHtmlAttributes: string[] | undefined = undefined,
    ): SafeHtml {
        if (!markdownText || markdownText === '') {
            return '';
        }
        const convertedString = this.htmlForMarkdown(markdownText, [...extensions, ...addCSSClass], allowedHtmlTags, allowedHtmlAttributes);
        return this.sanitizer.bypassSecurityTrustHtml(convertedString);
    }

    /**
     * Converts markdown into html, sanitizes it and then declares it as safe to bypass further security.
     *
     * @param {string} markdownText the original markdown text
     * @param {boolean} contentBeforeReference  to indicate if this is markdown content before a possible reference or after
     * @param {string[]} allowedHtmlTags to allow during sanitization
     * @param {string[]} allowedHtmlAttributes to allow during sanitization
     * @returns {string} the resulting html as a SafeHtml object that can be inserted into the angular template
     */
    safeHtmlForPostingMarkdown(
        markdownText?: string,
        contentBeforeReference = true,
        allowedHtmlTags: string[] | undefined = undefined,
        allowedHtmlAttributes: string[] | undefined = undefined,
    ): SafeHtml {
        if (!markdownText || markdownText === '') {
            return '';
        }
        let convertedString = this.htmlForMarkdown(markdownText, [], allowedHtmlTags, allowedHtmlAttributes);
        let paragraphPosition: number;
        if (contentBeforeReference) {
            paragraphPosition = convertedString.lastIndexOf('<p>');
        } else {
            paragraphPosition = convertedString.indexOf('<p>');
        }
        convertedString = convertedString.slice(0, paragraphPosition) + convertedString.slice(paragraphPosition).replace('<p>', '<p class="inline-paragraph">');
        return this.sanitizer.bypassSecurityTrustHtml(convertedString);
    }

    /**
     * Converts markdown into html (string) and sanitizes it. Does NOT declare it as safe to bypass further security
     * Note: If possible, please use safeHtmlForMarkdown
     *
     * @param {string} markdownText the original markdown text
     * @param {showdown.ShowdownExtension[]} extensions to use for markdown parsing
     * @param {string[]} allowedHtmlTags to allow during sanitization
     * @param {string[]} allowedHtmlAttributes to allow during sanitization
     * @returns {string} the resulting html as a SafeHtml object that can be inserted into the angular template
     */
    htmlForMarkdown(
        markdownText?: string,
        extensions: showdown.ShowdownExtension[] = [],
        allowedHtmlTags: string[] | undefined = undefined,
        allowedHtmlAttributes: string[] | undefined = undefined,
    ): string {
        if (!markdownText || markdownText === '') {
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
            extensions: [...extensions, showdownKatex(), showdownHighlight({ pre: true }), ...addCSSClass],
        });
        const html = converter.makeHtml(markdownText);
        const purifyParameters = {};
        if (allowedHtmlTags) {
            purifyParameters['ALLOWED_TAGS'] = allowedHtmlTags;
        }
        if (allowedHtmlAttributes) {
            purifyParameters['ALLOWED_ATTR'] = allowedHtmlAttributes;
        }
        return DOMPurify.sanitize(html, purifyParameters);
    }

    /**
     * Converts markdown into html, sanitizes it and then declares it as safe to bypass further security.
     *
     * @param {string} markdownText the original markdown text
     * @returns {string} the resulting html as a string
     */
    htmlForGuidedTourMarkdown(markdownText?: string): SafeHtml {
        if (!markdownText || markdownText === '') {
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
