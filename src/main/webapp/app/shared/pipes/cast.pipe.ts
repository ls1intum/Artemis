import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'cast' })
export class CastPipe implements PipeTransform {
    /* eslint-disable @typescript-eslint/no-unused-vars */
    transform<T>(value: any, type: new (...args: any[]) => T): T {
        return value as T;
    }
}
