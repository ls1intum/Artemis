import { Injectable } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import type { PluginSimple } from 'markdown-it';

@Injectable({ providedIn: 'root' })
export class ArtemisMarkdownService {
    constructor(private sanitizer: DomSanitizer) {}

    /**
     * Converts markdown into html, sanitizes it and then declares it as safe to bypass further security.
     *
     * @param {string} markdownText the original markdown text
     * @param extensions to use for markdown parsing
     * @param {string[]} allowedHtmlTags to allow during sanitization
     * @param {string[]} allowedHtmlAttributes to allow during sanitization
     * @returns {string} the resulting html as a SafeHtml object that can be inserted into the angular template
     */
    safeHtmlForMarkdown(
        markdownText?: string,
        extensions: PluginSimple[] = [],
        allowedHtmlTags: string[] | undefined = undefined,
        allowedHtmlAttributes: string[] | undefined = undefined,
    ): SafeHtml {
        if (!markdownText || markdownText === '') {
            return '';
        }
        const convertedString = htmlForMarkdown(markdownText, extensions, allowedHtmlTags, allowedHtmlAttributes);
        return this.sanitizer.bypassSecurityTrustHtml(convertedString);
    }

    /**
     * Converts markdown used in posting content, into html, sanitizes it and then declares it as safe to bypass further security.
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
        let convertedString = htmlForMarkdown(markdownText, [], allowedHtmlTags, allowedHtmlAttributes);
        // determine the first paragraph before (in contentBeforeReference) and the first paragraph after (in contentAfterReference) a reference
        let paragraphPosition: number;
        if (contentBeforeReference) {
            paragraphPosition = convertedString.lastIndexOf('<p>');
        } else {
            paragraphPosition = convertedString.indexOf('<p>');
        }
        // the first paragraph before and the first paragraph after a reference need the class `inline-paragraph` in order have no unintended linebreaks
        convertedString = convertedString.slice(0, paragraphPosition) + convertedString.slice(paragraphPosition).replace('<p>', '<p class="inline-paragraph">');
        return this.sanitizer.bypassSecurityTrustHtml(convertedString);
    }
}
