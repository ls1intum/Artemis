import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'removekeys' })
export class RemoveKeysPipe implements PipeTransform {
    transform(items: Array<{ key: string; value: any }>, keys: Array<string>): any {
        if (!items || !keys) {
            return items;
        }
        return items.filter(({ key }) => !keys.includes(key));
    }
}
