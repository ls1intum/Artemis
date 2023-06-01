import { Pipe, PipeTransform } from '@angular/core';
import linkifyStr from 'linkify-string';
import { LinkifyOptions } from 'app/shared/link-preview/linkify/interfaces/linkify.interface';

@Pipe({
    name: 'linkify',
})
export class LinkifyPipe implements PipeTransform {
    transform(value: string, options?: LinkifyOptions): string {
        return value ? linkifyStr(value, options) : value;
    }
}
