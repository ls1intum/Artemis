import { Injectable } from '@angular/core';
import { get } from 'lodash';

@Injectable({
    providedIn: 'root',
})
export class SortService {
    constructor() {}

    sortByProperty<T>(array: T[], key: string, asc: boolean): T[] {
        array.sort((a: T, b: T) => {
            const valueA = get(a, key);
            const valueB = get(b, key);

            if (valueA === valueB) {
                return 0;
            } else if (valueA === null) {
                return 1;
            } else if (valueB === null) {
                return -1;
            } else if (asc) {
                return valueA < valueB ? -1 : 1;
            } else {
                return valueA < valueB ? 1 : -1;
            }
        });
        return array;
    }
}
