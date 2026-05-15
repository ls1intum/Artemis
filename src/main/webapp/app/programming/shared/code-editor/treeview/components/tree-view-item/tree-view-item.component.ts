import { Component, TemplateRef, input, output } from '@angular/core';
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
    readonly template = input<TemplateRef<TreeViewItemTemplateContext<T>>>(undefined!);
    readonly item = input<TreeViewItem<T>>(undefined!);
    readonly checkedChange = output<boolean>();

    onCollapseExpand = () => {
        const item = this.item();
        if (item) {
            item.collapsed = !item.collapsed;
        }
    };

    onCheckedChange = () => {
        const item = this.item();
        if (!item) {
            return;
        }
        const checked = item.checked;
        this.checkedChange.emit(checked);
    };

    onChildCheckedChange(child: TreeViewItem<T>, checked: boolean): void {
        const item = this.item();
        if (!item) {
            this.checkedChange.emit(checked);
            return;
        }
        let itemChecked: boolean | undefined = undefined;
        for (const childItem of item.children) {
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

        if (item.checked !== itemChecked) {
            item.checked = itemChecked;
        }

        this.checkedChange.emit(checked);
    }
}
