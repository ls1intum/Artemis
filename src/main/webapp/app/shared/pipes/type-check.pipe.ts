import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'typeCheck' })
export class TypeCheckPipe implements PipeTransform {
    transform(items: Array<any>, classType: Function): any[] {
        return items.filter(item => item instanceof classType);
    }
}
