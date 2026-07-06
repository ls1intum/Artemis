import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'as',
    pure: true,
})
export class AsPipe implements PipeTransform {
    transform<T>(value: unknown, _type: (new (...args: never[]) => T) | T): T {
        return value as T;
    }
}
