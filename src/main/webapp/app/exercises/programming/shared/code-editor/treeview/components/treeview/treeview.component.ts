import { Component, Input, Output, EventEmitter, SimpleChanges, OnChanges, TemplateRef, OnInit } from '@angular/core';
import { isNil, includes } from 'lodash';
import { TreeviewI18n } from '../../models/treeview-i18n';
import { TreeviewItem, TreeviewSelection } from '../../models/treeview-item';
import { TreeviewConfig } from '../../models/treeview-config';
import { TreeviewHeaderTemplateContext } from '../../models/treeview-header-template-context';
import { TreeviewItemTemplateContext } from '../../models/treeview-item-template-context';
import { TreeviewHelper } from '../../helpers/treeview-helper';
import { TreeviewEventParser } from '../../helpers/treeview-event-parser';

class FilterTreeviewItem extends TreeviewItem {
    private readonly refItem: TreeviewItem;
    constructor(item: TreeviewItem) {
        super({
            text: item.text,
            value: item.value,
            disabled: item.disabled,
            checked: item.checked,
            collapsed: item.collapsed,
            children: item.children,
        });
        this.refItem = item;
    }

    updateRefChecked(): void {
        this.children.forEach((child) => {
            if (child instanceof FilterTreeviewItem) {
                child.updateRefChecked();
            }
        });

        let refChecked = this.checked;
        if (refChecked) {
            for (const refChild of this.refItem.children) {
                if (!refChild.checked) {
                    refChecked = false;
                    break;
                }
            }
        }
        this.refItem.checked = refChecked;
    }
}

@Component({
    // tslint:disable-next-line:component-selector
    selector: 'treeview',
    templateUrl: './treeview.component.html',
    styleUrls: ['./treeview.component.scss'],
})
export class TreeviewComponent implements OnChanges, OnInit {
    @Input() headerTemplate: TemplateRef<TreeviewHeaderTemplateContext>;
    @Input() itemTemplate: TemplateRef<TreeviewItemTemplateContext>;
    @Input() items: TreeviewItem[];
    @Input() config: TreeviewConfig;
    @Output() selectedChange = new EventEmitter<any[]>();
    @Output() filterChange = new EventEmitter<string>();
    headerTemplateContext: TreeviewHeaderTemplateContext;
    allItem: TreeviewItem;
    filterText = '';
    filterItems: TreeviewItem[];
    selection: TreeviewSelection;

    constructor(public i18n: TreeviewI18n, private defaultConfig: TreeviewConfig, private eventParser: TreeviewEventParser) {
        this.config = this.defaultConfig;
        this.allItem = new TreeviewItem({ text: 'All', value: undefined });
    }

    get hasFilterItems(): boolean {
        return !isNil(this.filterItems) && this.filterItems.length > 0;
    }

    get maxHeight(): string {
        return `${this.config.maxHeight}`;
    }

    ngOnInit(): void {
        this.createHeaderTemplateContext();
        this.generateSelection();
    }

    ngOnChanges(changes: SimpleChanges): void {
        const itemsSimpleChange = changes.items;
        if (!isNil(itemsSimpleChange) && !isNil(this.items)) {
            this.updateFilterItems();
            this.updateCollapsedOfAll();
            this.raiseSelectedChange();
        }
    }

    onAllCollapseExpand(): void {
        this.allItem.collapsed = !this.allItem.collapsed;
        this.filterItems.forEach((item) => item.setCollapsedRecursive(this.allItem.collapsed));
    }

    onFilterTextChange(text: string): void {
        this.filterText = text;
        this.filterChange.emit(text);
        this.updateFilterItems();
    }

    onAllCheckedChange(): void {
        const checked = this.allItem.checked;
        this.filterItems.forEach((item) => {
            item.setCheckedRecursive(checked);
            if (item instanceof FilterTreeviewItem) {
                item.updateRefChecked();
            }
        });

        this.raiseSelectedChange();
    }

    onItemCheckedChange(item: TreeviewItem, checked: boolean): void {
        if (item instanceof FilterTreeviewItem) {
            item.updateRefChecked();
        }

        this.updateCheckedOfAll();
        this.raiseSelectedChange();
    }

    raiseSelectedChange(): void {
        this.generateSelection();
        const values = this.eventParser.getSelectedChange(this);
        setTimeout(() => {
            this.selectedChange.emit(values);
        });
    }

    private createHeaderTemplateContext(): void {
        this.headerTemplateContext = {
            config: this.config,
            item: this.allItem,
            onCheckedChange: () => this.onAllCheckedChange(),
            onCollapseExpand: () => this.onAllCollapseExpand(),
            onFilterTextChange: (text) => this.onFilterTextChange(text),
        };
    }

    private generateSelection(): void {
        let checkedItems: TreeviewItem[] = [];
        let uncheckedItems: TreeviewItem[] = [];
        if (!isNil(this.items)) {
            const selection = TreeviewHelper.concatSelection(this.items, checkedItems, uncheckedItems);
            checkedItems = selection.checked;
            uncheckedItems = selection.unchecked;
        }

        this.selection = {
            checkedItems,
            uncheckedItems,
        };
    }

    private updateFilterItems(): void {
        if (this.filterText !== '') {
            const filterItems: TreeviewItem[] = [];
            const filterText = this.filterText.toLowerCase();
            this.items.forEach((item) => {
                const newItem = this.filterItem(item, filterText);
                if (!isNil(newItem)) {
                    filterItems.push(newItem);
                }
            });
            this.filterItems = filterItems;
        } else {
            this.filterItems = this.items;
        }

        this.updateCheckedOfAll();
    }

    private filterItem(item: TreeviewItem, filterText: string): TreeviewItem | undefined {
        const isMatch = includes(item.text.toLowerCase(), filterText);
        if (isMatch) {
            return item;
        } else {
            if (!isNil(item.children)) {
                const children: TreeviewItem[] = [];
                item.children.forEach((child) => {
                    const newChild = this.filterItem(child, filterText);
                    if (!isNil(newChild)) {
                        children.push(newChild);
                    }
                });
                if (children.length > 0) {
                    const newItem = new FilterTreeviewItem(item);
                    newItem.collapsed = false;
                    newItem.children = children;
                    return newItem;
                }
            }
        }
        return undefined;
    }

    private updateCheckedOfAll(): void {
        let itemChecked: boolean | undefined = undefined;
        for (const filterItem of this.filterItems) {
            if (itemChecked == undefined) {
                itemChecked = filterItem.checked;
            } else if (itemChecked !== filterItem.checked) {
                itemChecked = undefined;
                break;
            }
        }

        if (itemChecked == undefined) {
            itemChecked = false;
        }

        this.allItem.checked = itemChecked;
    }

    private updateCollapsedOfAll(): void {
        let hasItemExpanded = false;
        for (const filterItem of this.filterItems) {
            if (!filterItem.collapsed) {
                hasItemExpanded = true;
                break;
            }
        }

        this.allItem.collapsed = !hasItemExpanded;
    }
}
