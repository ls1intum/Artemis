import { Pipe, PipeTransform, inject } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';

@Pipe({
    name: 'htmlForPostingMarkdown',
})
export class HtmlForPostingMarkdownPipe implements PipeTransform {
    private markdownService = inject(ArtemisMarkdownService);

    /**
     * Converts markdown used in posting content into html, sanitizes it and then declares it as safe to bypass further security.
     * @param {string} markdown the original markdown text
     * @param {boolean} contentBeforeReference to indicate if this is markdown content before a possible reference or after
     * @param {string[]} allowedHtmlTags to allow during sanitization
     * @param {string[]} allowedHtmlAttributes to allow during sanitization
     * @returns {string} the resulting html as a SafeHtml object that can be inserted into the angular template
     */
    transform(
        markdown?: string,
        contentBeforeReference = true,
        allowedHtmlTags: string[] | undefined = undefined,
        allowedHtmlAttributes: string[] | undefined = undefined,
    ): SafeHtml {
        return this.markdownService.safeHtmlForPostingMarkdown(markdown, contentBeforeReference, allowedHtmlTags, allowedHtmlAttributes);
    }
}
