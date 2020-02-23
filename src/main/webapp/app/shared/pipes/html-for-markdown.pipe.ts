import { Pipe, PipeTransform } from '@angular/core';
import { ShowdownExtension } from 'showdown';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdown } from 'app/shared/markdown.service';

@Pipe({
    name: 'htmlForMarkdown',
})
export class HtmlForMarkdownPipe implements PipeTransform {
    constructor(private markdownService: ArtemisMarkdown) {}
    transform(markdown: string, extensions: ShowdownExtension[] = []): SafeHtml | null {
        return this.markdownService.safeHtmlForMarkdown(markdown, extensions);
    }
}
