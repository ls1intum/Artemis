import { Injectable } from '@angular/core';
import * as moment from 'moment';

@Injectable({
    providedIn: 'root',
})
export class SortService {
    constructor() {}

    sortByProperty<T>(array: T[], key: string, asc: boolean): T[] {
        return array.sort((a: T, b: T) => {
            const valueA = this.customGet(a, key, undefined);
            const valueB = this.customGet(b, key, undefined);

            if ((!valueA && valueA !== 0) || (!valueB && valueB !== 0)) {
                return SortService.compareWithUndefinedNull(valueA, valueB);
            }

            if (moment.isMoment(valueA) && moment.isMoment(valueB)) {
                return SortService.compareMoments(valueA, valueB, asc);
            }

            return SortService.compareBasic(valueA, valueB, asc);
        });
    }

    private static compareWithUndefinedNull(valueA: any, valueB: any) {
        if (!valueA && !valueB) {
            return 0;
        } else if (!valueA) {
            return 1;
        } else {
            return -1;
        }
    }

    private static compareMoments(valueA: moment.Moment, valueB: moment.Moment, ascending: boolean) {
        if (valueA.isSame(valueB)) {
            return 0;
        } else if (ascending) {
            return valueA.isBefore(valueB) ? -1 : 1;
        } else {
            return valueA.isBefore(valueB) ? 1 : -1;
        }
    }

    private static compareBasic(valueA: any, valueB: any, ascending: boolean) {
        if (valueA === valueB) {
            return 0;
        } else if (ascending) {
            return valueA < valueB ? -1 : 1;
        } else {
            return valueA < valueB ? 1 : -1;
        }
    }

    private customGet(object: any, path: string, defaultValue: any) {
        const pathArray = path.split('.').filter((key) => key);
        const value = pathArray.reduce((obj, key) => {
            if (!obj) {
                return obj;
            } else {
                if (obj instanceof Map) {
                    return obj.get(key);
                } else {
                    return obj[key];
                }
            }
        }, object);

        if (value === undefined) {
            return defaultValue;
        } else {
            return value;
        }
    }
}
