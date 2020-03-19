import { Pipe, PipeTransform } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdown } from 'app/shared/markdown.service';

@Pipe({
    name: 'htmlForGuidedTourMarkdown',
})
export class HtmlForGuidedTourMarkdownPipe implements PipeTransform {
    constructor(private markdownService: ArtemisMarkdown) {}
    transform(markdown: string): SafeHtml | null {
        return this.markdownService.htmlForGuidedTourMarkdown(markdown);
    }
}
