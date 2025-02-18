import { TreeViewItem } from '../models/tree-view-item';

export function findItem<T>(root: TreeViewItem<T> | undefined, value: any): TreeViewItem<T> | undefined {
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

export function findItemInList<T>(list: TreeViewItem<T>[] | undefined, value: any): TreeViewItem<T> | undefined {
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
