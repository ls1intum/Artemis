import { Component, EventEmitter, Input, Output, TemplateRef } from '@angular/core';
import { TreeviewItem } from '../../models/treeview-item';
import { TreeviewItemTemplateContext } from '../../models/treeview-item-template-context';

@Component({
    // tslint:disable-next-line:component-selector
    selector: 'treeview-item',
    templateUrl: './treeview-item.component.html',
    styleUrls: ['./treeview-item.component.scss'],
})
export class TreeviewItemComponent<T> {
    @Input() template: TemplateRef<TreeviewItemTemplateContext<T>>;
    @Input() item: TreeviewItem<T>;
    @Output() checkedChange = new EventEmitter<boolean>();

    onCollapseExpand = () => {
        this.item.collapsed = !this.item.collapsed;
    };

    onCheckedChange = () => {
        const checked = this.item.checked;
        this.checkedChange.emit(checked);
    };

    onChildCheckedChange(child: TreeviewItem<T>, checked: boolean): void {
        let itemChecked: boolean | undefined = undefined;
        for (const childItem of this.item.children) {
            if (itemChecked == undefined) {
                itemChecked = childItem.checked;
            } else if (itemChecked !== childItem.checked) {
                itemChecked = undefined;
                break;
            }
        }

        if (itemChecked == undefined) {
            itemChecked = false;
        }

        if (this.item.checked !== itemChecked) {
            this.item.checked = itemChecked;
        }

        this.checkedChange.emit(checked);
    }
}
