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
    // Optional: callers may omit this to use the built-in defaultItemTemplate fallback in the template.
    readonly itemTemplate = input<TemplateRef<TreeViewItemTemplateContext<T>>>();
    readonly items = input.required<TreeViewItem<T>[]>();
    readonly maxHeight = input(500);
    readonly filterChange = output<string>();
}
