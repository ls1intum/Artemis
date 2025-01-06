import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'keys' })
export class KeysPipe implements PipeTransform {
    /**
     * Returns an array containing the property names of the given object.
     * @param obj The object to retrieve the keys from.
     */
    transform(obj: { [key: string]: any }): string[] {
        return Object.keys(obj);
    }
}
