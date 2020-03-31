import { Pipe, PipeTransform } from '@angular/core';

/**
 * Removes items from an array that are not an instance of the specified classType.
 **/
@Pipe({ name: 'typeCheck' })
export class TypeCheckPipe implements PipeTransform {
    transform(items: Array<any>, classType: Function): any[] {
        return items.filter((item) => item instanceof classType);
    }
}
