import { Injectable } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { addCSSClass, htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import showdown from 'showdown';

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
}
