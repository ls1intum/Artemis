import { Pipe, PipeTransform } from '@angular/core';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';

@Pipe({
    name: 'htmlForMarkdown',
})
export class HtmlForMarkdownPipe implements PipeTransform {
    constructor(private markdownService: ArtemisMarkdown) {}
    transform(markdown: string): string {
        return this.markdownService.htmlForMarkdown(markdown);
    }
}
