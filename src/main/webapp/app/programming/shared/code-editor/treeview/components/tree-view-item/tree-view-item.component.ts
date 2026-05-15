import { Component, Input, TemplateRef, input, output } from '@angular/core';
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
    // TODO: Skipped for migration because:
    //  This input is used in a control flow expression (e.g. `@if` or `*ngIf`)
    //  and migrating would break narrowing currently.
    @Input() item: TreeViewItem<T>;
    readonly checkedChange = output<boolean>();

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
