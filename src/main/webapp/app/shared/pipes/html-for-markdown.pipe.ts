import { Pipe, PipeTransform } from '@angular/core';
import { ShowdownExtension } from 'showdown';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';

@Pipe({
    name: 'htmlForMarkdown',
})
export class HtmlForMarkdownPipe implements PipeTransform {
    constructor(private markdownService: ArtemisMarkdownService) {}
    transform(markdown: string, extensions: ShowdownExtension[] = []): SafeHtml | null {
        return this.markdownService.safeHtmlForMarkdown(markdown, extensions);
    }
}
