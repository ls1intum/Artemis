import { Pipe, PipeTransform } from '@angular/core';
import { TreeviewItem } from '../models/treeview-item';

@Pipe({
    name: 'treeview',
})
export class TreeviewPipe implements PipeTransform {
    transform(objects: any[], textField: string): TreeviewItem[] | undefined {
        if (!objects) {
            return undefined;
        }

        return objects.map((object) => new TreeviewItem({ text: object[textField], value: object }));
    }
}
