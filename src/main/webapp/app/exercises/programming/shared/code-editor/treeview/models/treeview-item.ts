import { isBoolean, isString } from 'lodash-es';

export interface TreeItem<T> {
    text: string;
    value: T;
    disabled?: boolean;
    checked?: boolean;
    collapsed?: boolean;
    children: TreeItem<T>[];
}

export class TreeviewItem<T> {
    private internalDisabled = false;
    private internalCollapsed = false;
    private internalChildren: TreeviewItem<T>[] = [];
    checked = false;
    text: string;
    value: T;

    constructor(item: TreeItem<T>) {
        if (!item) {
            throw new Error('Item must be defined');
        }
        if (isString(item.text)) {
            this.text = item.text;
        } else {
            throw new Error('A text of item must be string object');
        }
        this.value = item.value;
        if (isBoolean(item.checked)) {
            this.checked = item.checked;
        }
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

    get children(): TreeviewItem<T>[] {
        return this.internalChildren;
    }

    set children(value: TreeviewItem<T>[]) {
        if (this.internalChildren !== value) {
            this.internalChildren = value;
        }
    }
}
