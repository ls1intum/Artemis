import { Pipe, PipeTransform } from '@angular/core';

/**
 * Checks whether an element matches a search value on any of the given fields. With `nestedField` set, a match on
 * any nested entry also counts, keeping container items (e.g. a variant-group card) visible.
 * @param element the element to test
 * @param fields the field names to match against
 * @param value the search value; matching is case-insensitive
 * @param nestedField optional name of a nested array field whose entries are matched one level deep
 * @returns true if the element itself or any nested entry matches
 */
export function matchesSearch<T>(element: T, fields: string[], value: string, nestedField?: string): boolean {
    if (!element) {
        return false;
    }
    const record = element as Record<string, unknown>;
    const matchesOwnFields = fields.some((field) => {
        const elementValue = record[field];
        return elementValue && typeof elementValue === 'string' ? elementValue.toLowerCase().includes(value?.toLowerCase()) : false;
    });
    if (matchesOwnFields || !nestedField) {
        return matchesOwnFields;
    }
    const nestedItems = record[nestedField];
    // Only one level deep: nested items never carry nested items themselves.
    return Array.isArray(nestedItems) && nestedItems.some((nestedItem) => matchesSearch(nestedItem, fields, value));
}

@Pipe({ name: 'searchFilter' })
export class SearchFilterPipe implements PipeTransform {
    transform<T>(array: T[] | undefined, fields: string[], value: string, nestedField?: string): T[] {
        if (!Array.isArray(array)) {
            return [];
        }
        return array?.filter((element) => matchesSearch(element, fields, value, nestedField));
    }
}
