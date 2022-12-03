import { Component, EventEmitter, Input, Output, TemplateRef } from '@angular/core';
import { TreeviewItem } from '../../models/treeview-item';
import { TreeviewItemTemplateContext } from '../../models/treeview-item-template-context';

@Component({
    // tslint:disable-next-line:component-selector
    selector: 'treeview',
    templateUrl: './treeview.component.html',
    styleUrls: ['./treeview.component.scss'],
})
export class TreeviewComponent<T> {
    @Input() itemTemplate: TemplateRef<TreeviewItemTemplateContext<T>>;
    @Input() items: TreeviewItem<T>[];
    @Input() maxHeight = 500;
    @Output() filterChange = new EventEmitter<string>();
}
