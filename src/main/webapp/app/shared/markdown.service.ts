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

    /**
     * Extracts anchor tags from the given HTML and returns them as an array of objects.
     *
     * @param safeHtml the HTML to extract anchor tags from
     * @returns an array of objects with the href and title of the anchor tags
     */
    private extractAnchorTagsFromHTML(safeHtml: string): { href: string; title: string }[] {
        const div = document.createElement('div');
        div.innerHTML = this.sanitizer.sanitize(0, safeHtml) || '';

        const links: { href: string; title: string }[] = [];
        const anchorElements = div.getElementsByTagName('a');

        for (let i = 0; i < anchorElements.length; i++) {
            const anchor = anchorElements[i];
            links.push({
                href: anchor.getAttribute('href') || '',
                title: anchor.textContent || '',
            });
        }

        return links;
    }

    /**
     * Extracts anchor tags from the given markdown and returns them as an array of objects.
     *
     * @param markdown the markdown to extract anchor tags from
     * @returns an array of objects with the href and title of the anchor tags
     */
    extractAnchorTagsFromMarkdown(markdown: string): { href: string; title: string }[] {
        const html = htmlForMarkdown(markdown);
        return this.extractAnchorTagsFromHTML(html);
    }
}
