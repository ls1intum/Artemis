import { Injectable } from '@angular/core';
import dayjs from 'dayjs/esm';

@Injectable({
    providedIn: 'root',
})
export class SortService {
    constructor() {}

    /**
     * Sorts the given array based on the defined key
     * @param array The array that should be sorted
     * @param key The attribute of the elements that should be used for determining the order
     * @param ascending Decides if the biggest value comes last (ascending) or first (descending)
     */
    sortByProperty<T>(array: T[], key: string, ascending: boolean): T[] {
        return this.sortByFunction(array, (element) => SortService.customGet(element, key, undefined), ascending);
    }

    /**
     * Sorts the given array based on the defined key
     * @param array The array that should be sorted
     * @param func The function that returns a value based on which the elements should be sorted
     * @param ascending Decides if the biggest value comes last (ascending) or first (descending)
     */
    sortByFunction<T>(array: T[], func: { (parameter: T): any }, ascending: boolean): T[] {
        return array.sort((a: T, b: T) => {
            const valueA = func(a);
            const valueB = func(b);

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
        if ((valueA === null || valueA === undefined) && (valueB === null || valueB === undefined)) {
            return 0;
        } else if (valueA === null || valueA === undefined) {
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
