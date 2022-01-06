import { Injectable } from '@angular/core';
import dayjs from 'dayjs/esm';

@Injectable({
    providedIn: 'root',
})
export class SortService {
    constructor() {}

    sortByProperty<T>(array: T[], key: string, asc: boolean): T[] {
        return array.sort((a: T, b: T) => {
            const valueA = this.customGet(a, key, undefined);
            const valueB = this.customGet(b, key, undefined);

            if (valueA == undefined || valueB == undefined) {
                return SortService.compareWithUndefinedNull(valueA, valueB);
            }

            if (dayjs.isDayjs(valueA) && dayjs.isDayjs(valueB)) {
                return SortService.compareDayjs(valueA, valueB, asc);
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

    private static compareDayjs(valueA: dayjs.Dayjs, valueB: dayjs.Dayjs, ascending: boolean) {
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
