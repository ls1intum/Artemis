import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'postingContentMarkdownLinebreak',
})
export class PostingContentMarkdownLinebreakPipe implements PipeTransform {
    /**
     * Replaces a markdown linebreak into an html tag to ensure that further transformation in htmlForMarkdownPipe will not remove linebreaks
     * @param {string} markdown the original markdown text
     */
    transform(markdown?: string): string {
        if (markdown) {
            return markdown.replace(/\n/gm, '<br>');
        } else {
            return '';
        }
    }
}
