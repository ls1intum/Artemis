import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'typeCheck' })
export class TypeCheckPipe implements PipeTransform {
    /**
     * Filters items from an array that are not an instance of the specified classType.
     * @param items The array of items to filter.
     * @param classType The class' type that array items are checked against.
     */
    transform<T, R extends T>(items: Array<T>, classType: abstract new (...args: never[]) => R): R[] {
        return items.filter((item): item is R => item instanceof classType);
    }
}
