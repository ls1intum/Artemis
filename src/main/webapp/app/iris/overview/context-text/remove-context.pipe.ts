import { Pipe, PipeTransform } from '@angular/core';
import { removeContextBlock } from './iris-context-text.model';

@Pipe({
    name: 'removeContext',
    standalone: true,
})
export class RemoveContextPipe implements PipeTransform {
    transform(value: string | null | undefined): string {
        if (!value) {
            return '';
        }
        return removeContextBlock(value);
    }
}
