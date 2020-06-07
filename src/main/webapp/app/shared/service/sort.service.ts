import { Injectable } from '@angular/core';
import { get } from 'lodash';
import * as moment from 'moment';

@Injectable({
    providedIn: 'root',
})
export class SortService {
    constructor() {}

    sortByProperty<T>(array: T[], key: string, asc: boolean): T[] {
        array.sort((a: T, b: T) => {
            const valueA = get(a, key, null);
            const valueB = get(b, key, null);

            if (valueA === null || valueB === null) {
                return this.compareWithNull(valueA, valueB);
            }

            if (moment.isMoment(valueA) && moment.isMoment(valueB)) {
                return this.compareMoments(valueA, valueB, asc);
            }

            return this.compareBasic(valueA, valueB, asc);
        });
        return array;
    }

    private compareWithNull(valueA: any | null, valueB: any | null) {
        if (valueA === null && valueB === null) {
            return 0;
        } else if (valueA === null) {
            return 1;
        } else {
            return -1;
        }
    }

    private compareMoments(valueA: moment.Moment, valueB: moment.Moment, ascending: boolean) {
        if (valueA.isSame(valueB)) {
            return 0;
        } else if (ascending) {
            return valueA.isBefore(valueB) ? -1 : 1;
        } else {
            return valueA.isBefore(valueB) ? 1 : -1;
        }
    }

    private compareBasic(valueA: any, valueB: any, ascending: boolean) {
        if (valueA === valueB) {
            return 0;
        } else if (ascending) {
            return valueA < valueB ? -1 : 1;
        } else {
            return valueA < valueB ? 1 : -1;
        }
    }
}
