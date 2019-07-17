import { Pipe, PipeTransform } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';

@Pipe({
    name: 'htmlForMarkdown',
})
export class HtmlForMarkdownPipe implements PipeTransform {
    constructor(private markdownService: ArtemisMarkdown) {}
    transform(markdown: string): SafeHtml | null {
        return this.markdownService.htmlForMarkdown(markdown);
    }
}
