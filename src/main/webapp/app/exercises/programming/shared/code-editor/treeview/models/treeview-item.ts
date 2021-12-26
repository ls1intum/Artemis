import { isBoolean, isString } from 'lodash-es';
import { TreeviewHelper } from '../helpers/treeview-helper';

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
    private internalChecked: boolean | undefined = true;
    private internalCollapsed = false;
    private internalChildren: TreeviewItem[] = [];
    text: string;
    value: any;

    constructor(item: TreeItem, autoCorrectChecked = false) {
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

        if (autoCorrectChecked) {
            this.correctChecked();
        }
    }

    get checked(): boolean | undefined {
        return this.internalChecked;
    }

    set checked(value: boolean | undefined) {
        if (!this.internalDisabled) {
            if (this.internalChecked !== value) {
                this.internalChecked = value;
            }
        }
    }

    setCheckedRecursive(value: boolean | undefined): void {
        if (!this.internalDisabled) {
            this.internalChecked = value;
            if (this.internalChildren) {
                this.internalChildren.forEach((child) => child.setCheckedRecursive(value));
            }
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
            if (this.internalChildren) {
                let checked: boolean | undefined = undefined;
                this.internalChildren.forEach((child) => {
                    if (checked == undefined) {
                        checked = child.checked;
                    } else {
                        if (child.checked !== checked) {
                            checked = undefined;
                            return;
                        }
                    }
                });
                this.internalChecked = checked;
            }
        }
    }

    getSelection(): TreeviewSelection {
        let checkedItems: TreeviewItem[] = [];
        let uncheckedItems: TreeviewItem[] = [];
        if (!this.internalChildren) {
            if (this.internalChecked) {
                checkedItems.push(this);
            } else {
                uncheckedItems.push(this);
            }
        } else {
            const selection = TreeviewHelper.concatSelection(this.internalChildren, checkedItems, uncheckedItems);
            checkedItems = selection.checked;
            uncheckedItems = selection.unchecked;
        }

        return {
            checkedItems,
            uncheckedItems,
        };
    }

    correctChecked(): void {
        this.internalChecked = this.getCorrectChecked();
    }

    private getCorrectChecked(): boolean | undefined {
        let checked: boolean | undefined = undefined;
        if (this.internalChildren) {
            for (const child of this.internalChildren) {
                child.internalChecked = child.getCorrectChecked();
                if (checked == undefined) {
                    checked = child.internalChecked;
                } else if (checked !== child.internalChecked) {
                    checked = undefined;
                    break;
                }
            }
        } else {
            checked = this.checked;
        }

        return checked;
    }
}
