import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'negatedTypeCheck' })
export class NegatedTypeCheckPipe implements PipeTransform {
    /**
     * Filters items from an array that are an instance of the specified classType.
     * @param items The array of items to filter.
     * @param classType The class' type that array items are checked against.
     */
    transform(items: Array<any>, classType: Function): any[] {
        return items.filter((item) => !(item instanceof classType));
    }
}
