import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'removekeys' })
export class RemoveKeysPipe implements PipeTransform {
    /**
     * Removes the specified keys from the array of objects.
     * @param items The array of objects whose keys will be removed.
     * @param keys The array with keys to be removed.
     */
    transform(items: Array<{ key: string; value: any }>, keys: Array<string>): any {
        if (!items || !keys) {
            return items;
        }
        return items.filter(({ key }) => !keys.includes(key));
    }
}
