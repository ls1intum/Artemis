import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'keys' })
export class KeysPipe implements PipeTransform {
    transform(obj: { [key: string]: any }): string[] {
        return Object.keys(obj);
    }
}
