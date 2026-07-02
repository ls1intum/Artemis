import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'searchFilter' })
export class SearchFilterPipe implements PipeTransform {
    transform<T>(array: T[] | undefined, fields: string[], value: string): T[] {
        if (!Array.isArray(array)) {
            return [];
        }
        return array?.filter((element) => {
            if (!element) {
                return false;
            }
            return fields.some((field) => {
                const elementValue = (element as Record<string, unknown>)[field];
                return elementValue && typeof elementValue === 'string' ? elementValue.toLowerCase().includes(value?.toLowerCase()) : false;
            });
        });
    }
}
