import { Pipe, PipeTransform } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import type { PluginSimple } from 'markdown-it';

@Pipe({
    name: 'htmlForMarkdown',
})
export class HtmlForMarkdownPipe implements PipeTransform {
    constructor(private markdownService: ArtemisMarkdownService) {}

    /**
     * Converts markdown into html, sanitizes it and then declares it as safe to bypass further security.
     * @param {string} markdown the original markdown text
     * @param {PluginSimple[]} extensions to use for markdown parsing
     * @param {string[]} allowedHtmlTags to allow during sanitization
     * @param {string[]} allowedHtmlAttributes to allow during sanitization
     * @returns {string} the resulting html as a SafeHtml object that can be inserted into the angular template
     */
    transform(
        markdown?: string,
        extensions: PluginSimple[] = [],
        allowedHtmlTags: string[] | undefined = undefined,
        allowedHtmlAttributes: string[] | undefined = undefined,
    ): SafeHtml {
        return this.markdownService.safeHtmlForMarkdown(markdown, extensions, allowedHtmlTags, allowedHtmlAttributes);
    }
}
