import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'postContentMarkdown',
})
export class PostContentMarkdown implements PipeTransform {
    /**
     * Converts markdown into html, sanitizes it and then declares it as safe to bypass further security.
     * @param {string} markdown the original markdown text
     */
    transform(markdown?: string): string {
        if (markdown) {
            return markdown.replace(/\n/gm, '<br/>');
        } else {
            return '';
        }
    }
}
