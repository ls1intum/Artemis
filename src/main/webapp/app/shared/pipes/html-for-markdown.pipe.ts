import { Pipe, PipeTransform } from '@angular/core';
import { ShowdownExtension } from 'showdown';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';

@Pipe({
    name: 'htmlForMarkdown',
})
export class HtmlForMarkdownPipe implements PipeTransform {
    constructor(private markdownService: ArtemisMarkdown) {}
    transform(markdown: string, extensions: ShowdownExtension[] = []): string | null {
        return this.markdownService.htmlForMarkdown(markdown, extensions);
    }
}
