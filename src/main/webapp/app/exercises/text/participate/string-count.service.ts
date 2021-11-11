import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class StringCountService {
    /**
     * includes Latin-1 Supplement 00C0 to 00FF to support german "umlaute"
     */
    private readonly wordMatchRegex = /[\w\u00C0-\u00ff]+/g;

    constructor() {}

    /**
     * Counts the number of words in a text
     * @param text
     */
    public countWords(text: string | null | undefined): number {
        let wordCount = 0;
        if (text) {
            const match = text.match(this.wordMatchRegex);
            if (match) {
                wordCount = match.length;
            }
        }
        return wordCount;
    }

    /**
     * Counts the number of characters in a text
     * @param text
     */
    public countCharacters(text: string | null | undefined): number {
        if (text) {
            return text.length;
        } else {
            return 0;
        }
    }
}
