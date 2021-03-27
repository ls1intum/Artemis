import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'typeof',
})
export class TypeOfPipe implements PipeTransform {
    /**
     * returns the string representation of the objects type.
     * @param value
     */
    transform(value: any): any {
        return typeof value;
    }
}
