import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'mathAbs',
})
export class MathAbsPipe implements PipeTransform {
    constructor() {}

    transform(value: number) {
        if (value) {
            return Math.abs(value);
        }
        return value;
    }
}
