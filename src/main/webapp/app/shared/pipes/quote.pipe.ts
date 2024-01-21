import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    standalone: true,
    name: 'quoted',
})
export class QuotePipe implements PipeTransform {
    /**
     * Wraps non-empty texts in quotes.
     *
     * @param text Some text.
     * @return The text in quotes if non-empty. An empty string otherwise.
     */
    transform(text: string | undefined): string {
        if (text) {
            return `"${text}"`;
        } else {
            return '';
        }
    }
}
