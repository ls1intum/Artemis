import { Injectable, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { addCSSClass, htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import showdown from 'showdown';

@Injectable({ providedIn: 'root' })
export class ArtemisMarkdownService {
    private sanitizer = inject(DomSanitizer);

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
        extensions: showdown.ShowdownExtension[] = [],
        allowedHtmlTags: string[] | undefined = undefined,
        allowedHtmlAttributes: string[] | undefined = undefined,
    ): SafeHtml {
        if (!markdownText || markdownText === '') {
            return '';
        }
        const convertedString = htmlForMarkdown(markdownText, [...extensions, ...addCSSClass], allowedHtmlTags, allowedHtmlAttributes);
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
