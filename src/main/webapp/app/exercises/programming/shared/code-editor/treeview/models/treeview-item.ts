import { isBoolean, isString } from 'lodash-es';

export interface TreeviewSelection {
    checkedItems: TreeviewItem[];
    uncheckedItems: TreeviewItem[];
}

export interface TreeItem {
    text: string;
    value: any;
    disabled?: boolean;
    checked?: boolean;
    collapsed?: boolean;
    children?: TreeItem[];
}

export class TreeviewItem {
    private internalDisabled = false;
    private internalCollapsed = false;
    private internalChildren: TreeviewItem[] = [];
    checked = false;
    text: string;
    value: any;

    constructor(item: TreeItem) {
        if (!item) {
            throw new Error('Item must be defined');
        }
        if (isString(item.text)) {
            this.text = item.text;
        } else {
            throw new Error('A text of item must be string object');
        }
        this.value = item.value;
        if (isBoolean(item.collapsed)) {
            this.collapsed = item.collapsed;
        }
        if (isBoolean(item.disabled)) {
            this.disabled = item.disabled;
        }
        if (item.children && item.children.length > 0) {
            this.children = item.children.map((child) => {
                if (this.disabled) {
                    child.disabled = true;
                }

                return new TreeviewItem(child);
            });
        }
    }

    get disabled(): boolean {
        return this.internalDisabled;
    }

    set disabled(value: boolean) {
        if (this.internalDisabled !== value) {
            this.internalDisabled = value;
            if (this.internalChildren) {
                this.internalChildren.forEach((child) => (child.disabled = value));
            }
        }
    }

    get collapsed(): boolean {
        return this.internalCollapsed;
    }

    set collapsed(value: boolean) {
        if (this.internalCollapsed !== value) {
            this.internalCollapsed = value;
        }
    }

    setCollapsedRecursive(value: boolean): void {
        this.internalCollapsed = value;
        if (this.internalChildren) {
            this.internalChildren.forEach((child) => child.setCollapsedRecursive(value));
        }
    }

    get children(): TreeviewItem[] {
        return this.internalChildren;
    }

    set children(value: TreeviewItem[]) {
        if (this.internalChildren !== value) {
            if (value?.length === 0) {
                throw new Error('Children must be not an empty array');
            }
            this.internalChildren = value;
        }
    }
}
