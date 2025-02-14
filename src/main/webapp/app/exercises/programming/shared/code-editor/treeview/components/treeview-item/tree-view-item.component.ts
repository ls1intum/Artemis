import { Component, EventEmitter, Input, Output, TemplateRef } from '@angular/core';
import { TreeViewItem } from '../../models/tree-view-item';
import { TreeViewItemTemplateContext } from '../../models/tree-view-item-template-context';
import { NgTemplateOutlet } from '@angular/common';

@Component({
    selector: 'treeview-item',
    templateUrl: './tree-view-item.component.html',
    styleUrls: ['./tree-view-item.component.scss'],
    imports: [NgTemplateOutlet],
})
export class TreeViewItemComponent<T> {
    @Input() template: TemplateRef<TreeViewItemTemplateContext<T>>;
    @Input() item: TreeViewItem<T>;
    @Output() checkedChange = new EventEmitter<boolean>();

    onCollapseExpand = () => {
        this.item.collapsed = !this.item.collapsed;
    };

    onCheckedChange = () => {
        const checked = this.item.checked;
        this.checkedChange.emit(checked);
    };

    onChildCheckedChange(child: TreeViewItem<T>, checked: boolean): void {
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
