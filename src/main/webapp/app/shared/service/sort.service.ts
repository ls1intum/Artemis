import { Injectable } from '@angular/core';
import dayjs from 'dayjs/esm';

@Injectable({
    providedIn: 'root',
})
export class SortService {
    constructor() {}

    sortByProperty<T>(array: T[], key: string, ascending: boolean): T[] {
        return array.sort((a: T, b: T) => {
            const valueA = SortService.customGet(a, key, undefined);
            const valueB = SortService.customGet(b, key, undefined);

            let compareValue;

            if (valueA == undefined || valueB == undefined) {
                compareValue = SortService.compareWithUndefinedNull(valueA, valueB);
            } else if (dayjs.isDayjs(valueA) && dayjs.isDayjs(valueB)) {
                compareValue = SortService.compareDayjs(valueA, valueB);
            } else {
                compareValue = SortService.compareBasic(valueA, valueB);
            }

            if (!ascending) {
                compareValue = -compareValue;
            }

            return compareValue;
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

    private static compareDayjs(valueA: dayjs.Dayjs, valueB: dayjs.Dayjs) {
        if (valueA.isSame(valueB)) {
            return 0;
        } else {
            return valueA.isBefore(valueB) ? -1 : 1;
        }
    }

    private static compareBasic(valueA: any, valueB: any) {
        if (valueA === valueB) {
            return 0;
        } else {
            return valueA < valueB ? -1 : 1;
        }
    }

    private static customGet(object: any, path: string, defaultValue: any) {
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
