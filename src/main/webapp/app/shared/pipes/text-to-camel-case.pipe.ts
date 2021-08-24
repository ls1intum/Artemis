import { PipeTransform, Pipe } from '@angular/core';

@Pipe({ name: 'camelCase' })
export class TextToCamelCasePipe implements PipeTransform {
    /**
     *  '/' : start/end of regex
     *  '[]' : match any character in the character set
     *  '\d' digit 0-9
     *  'A-Z' / 'a-z' : every character from (uppercase/lowercase) A-Z / a-z
     *  '\xC0-\xD6' special character range from "À" to "Ö"
     *  '\xDF-\xF6' special character range from "ß" to "ö"
     *  '?' match from 0 to 1 of preceding
     *  '+' match 1 or more of preceding
     *  '|' similar to boolean OR
     *  'g' global (iterative) search
     *  '(?!)' excludes group after main expression
     */
    regex = /[A-Z\xC0-\xD6]?[a-z\xDF-\xF6]+|[A-Z\xC0-\xD6]+(?![a-z\xDF-\xF6])|\d+/g;

    /**
     * Converts any input into String and then to (lower) camelCase.
     * @param value is input (text) to be converted into lowerCamelCase.
     */
    transform(value: any) {
        return this.toCamelCase(this.toWords(value));
    }

    /**
     * converts the provided input into an array of words split based on the regex
     */
    private toWords(value: any): string[] {
        value = typeof value === 'string' ? value : String(value);
        return value.match(this.regex);
    }

    /**
     * converts the provided array of words (string) into one string in (lower) camel case
     * @param words
     * @private
     */
    private toCamelCase(words: string[]): string {
        let result = '';
        for (let i = 0; i < words.length; i++) {
            let currentWord = words[i].toLowerCase();

            if (i != 0) {
                //convert first letter to upper case
                currentWord = currentWord.substr(0, 1).toUpperCase() + currentWord.substr(1);
            }
            result += currentWord;
        }
        return result;
    }
}
