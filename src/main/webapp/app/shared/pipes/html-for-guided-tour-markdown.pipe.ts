import { Pipe, PipeTransform } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';

@Pipe({
    name: 'htmlForGuidedTourMarkdown',
})
export class HtmlForGuidedTourMarkdownPipe implements PipeTransform {
    constructor(private markdownService: ArtemisMarkdownService) {}
    transform(markdown: string): SafeHtml | null {
        return this.markdownService.htmlForGuidedTourMarkdown(markdown);
    }
}
