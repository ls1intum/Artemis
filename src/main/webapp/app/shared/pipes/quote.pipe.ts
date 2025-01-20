import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'quoted',
})
export class QuotePipe implements PipeTransform {
    /**
     * Wraps non-empty texts in quotes.
     *
     * @param text Some text.
     * @param prefix An additional prefix that will be added before the opening quote in the non-empty case.
     * @return The text in quotes if non-empty. An empty string otherwise.
     */
    transform(text: string | undefined, prefix = ''): string {
        if (text) {
            return `${prefix}"${text}"`;
        } else {
            return '';
        }
    }
}
