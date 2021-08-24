import { PipeTransform, Pipe } from '@angular/core';

@Pipe({ name: 'lowerCamelCase' })
export class TextToLowerCamelCasePipe implements PipeTransform {
    /**
     * Converts a string (text) into lowerCamelCase.
     * @param input to be converted into lowerCamelCase.
     */
    transform(input: string) {
        if (input == undefined || input === '') {
            return '';
        }
        let words = input.split(' ');

        //remove extra white space
        words = words.filter((word) => word !== '');

        // converts words to lowerCamelCase
        let upperCamelCase = words.reduce((accumulator: string, currentWord: string) => {
            return accumulator + currentWord[0].toUpperCase() + currentWord.substr(1).toLowerCase();
        }, '');

        return upperCamelCase[0].toLowerCase() + upperCamelCase.substr(1);
    }
}
