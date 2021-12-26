import { TreeviewItem } from '../models/treeview-item';

export function findItem(root: TreeviewItem | undefined, value: any): TreeviewItem | undefined {
    if (!root) {
        return undefined;
    }

    if (root.value === value) {
        return root;
    }

    if (root.children) {
        for (const child of root.children) {
            const foundItem = findItem(child, value);
            if (foundItem) {
                return foundItem;
            }
        }
    }

    return undefined;
}

export function findItemInList(list: TreeviewItem[] | undefined, value: any): TreeviewItem | undefined {
    if (!list) {
        return undefined;
    }

    for (const item of list) {
        const foundItem = findItem(item, value);
        if (foundItem) {
            return foundItem;
        }
    }

    return undefined;
}
