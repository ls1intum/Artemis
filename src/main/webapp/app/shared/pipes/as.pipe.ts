import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'as',
    pure: true,
    standalone: false,
})
export class AsPipe implements PipeTransform {
    // eslint-disable-next-line
    transform<T>(value: any, _type: (new (...args: any[]) => T) | T): T {
        return value as T;
    }
}
