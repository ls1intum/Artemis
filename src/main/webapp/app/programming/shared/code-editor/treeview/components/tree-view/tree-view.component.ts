import { Component, TemplateRef, input, output } from '@angular/core';
import { TreeViewItem } from '../../models/tree-view-item';
import { TreeViewItemTemplateContext } from '../../models/tree-view-item-template-context';
import { FormsModule } from '@angular/forms';
import { TreeViewItemComponent } from 'app/programming/shared/code-editor/treeview/components/tree-view-item/tree-view-item.component';

@Component({
    selector: 'treeview',
    templateUrl: './tree-view.component.html',
    styleUrls: ['./tree-view.component.scss'],
    imports: [FormsModule, TreeViewItemComponent],
})
export class TreeViewComponent<T> {
    readonly itemTemplate = input<TemplateRef<TreeViewItemTemplateContext<T>>>(undefined!);
    readonly items = input<TreeViewItem<T>[]>(undefined!);
    readonly maxHeight = input(500);
    readonly filterChange = output<string>();
}
