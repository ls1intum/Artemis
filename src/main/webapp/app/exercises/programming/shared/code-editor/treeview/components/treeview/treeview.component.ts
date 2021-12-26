import { Component, Input, Output, EventEmitter, TemplateRef } from '@angular/core';
import { TreeviewItem } from '../../models/treeview-item';
import { TreeviewItemTemplateContext } from '../../models/treeview-item-template-context';

@Component({
    // tslint:disable-next-line:component-selector
    selector: 'treeview',
    templateUrl: './treeview.component.html',
    styleUrls: ['./treeview.component.scss'],
})
export class TreeviewComponent {
    @Input() itemTemplate: TemplateRef<TreeviewItemTemplateContext>;
    @Input() items: TreeviewItem[];
    @Input() maxHeight = 500;
    @Output() filterChange = new EventEmitter<string>();
}
