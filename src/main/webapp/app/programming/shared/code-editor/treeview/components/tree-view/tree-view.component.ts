import { Component, EventEmitter, Input, Output, TemplateRef } from '@angular/core';
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
    @Input() itemTemplate: TemplateRef<TreeViewItemTemplateContext<T>>;
    @Input() items: TreeViewItem<T>[];
    @Input() maxHeight = 500;
    @Output() filterChange = new EventEmitter<string>();
}
