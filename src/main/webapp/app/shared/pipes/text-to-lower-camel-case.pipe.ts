import { PipeTransform, Pipe } from '@angular/core';
import camelcase from 'camelcase';

@Pipe({ name: 'lowerCamelCase' })
export class TextToLowerCamelCasePipe implements PipeTransform {
    /**
     * Converts a string (text) into lowerCamelCase.
     * Uses the npm package camelcase https://www.npmjs.com/package/camelcase
     * Remark: camelcase('WoRd') -> woRd
     * @param input to be converted into lowerCamelCase.
     */
    transform(input: string) {
        if (input == undefined || input === '') {
            return '';
        }
        input = camelcase(input);
        return input[0].toLowerCase() + input.substr(1);
    }
}
