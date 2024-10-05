import { Pipe, PipeTransform, inject } from '@angular/core';
import { ShowdownExtension } from 'showdown';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';

@Pipe({
    name: 'htmlForMarkdown',
})
export class HtmlForMarkdownPipe implements PipeTransform {
    private markdownService = inject(ArtemisMarkdownService);

    /**
     * Converts markdown into html, sanitizes it and then declares it as safe to bypass further security.
     * @param {string} markdown the original markdown text
     * @param {ShowdownExtension[]} extensions to use for markdown parsing
     * @param {string[]} allowedHtmlTags to allow during sanitization
     * @param {string[]} allowedHtmlAttributes to allow during sanitization
     * @returns {string} the resulting html as a SafeHtml object that can be inserted into the angular template
     */
    transform(
        markdown?: string,
        extensions: ShowdownExtension[] = [],
        allowedHtmlTags: string[] | undefined = undefined,
        allowedHtmlAttributes: string[] | undefined = undefined,
    ): SafeHtml {
        return this.markdownService.safeHtmlForMarkdown(markdown, extensions, allowedHtmlTags, allowedHtmlAttributes);
    }
}
