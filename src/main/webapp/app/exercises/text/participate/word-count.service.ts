import { Injectable } from '@angular/core';

@Injectable({
    providedIn: 'root',
})
export class WordCountService {
    /**
     * includes Latin-1 Supplement 00C0 to 00FF to support german "umlaute"
     */
    private wordMatchRegex = /[\w\u00C0-\u00ff]+/g;

    constructor() {}

    public countWords(text: string): number {
        const match = text.match(this.wordMatchRegex);
        let wordCount = 0;
        if (match) {
            wordCount = match.length;
        }
        return wordCount;
    }
}
