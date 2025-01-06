import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'searchFilter' })
export class SearchFilterPipe implements PipeTransform {
    transform(array: any[] | undefined, fields: string[], value: string): any[] {
        if (!Array.isArray(array)) {
            return [];
        }
        return array?.filter((element) => {
            if (!element) {
                return false;
            }
            return fields.some((field) => {
                const elementValue = element[field];
                return elementValue && typeof elementValue === 'string' ? elementValue.toLowerCase().includes(value?.toLowerCase()) : false;
            });
        });
    }
}
