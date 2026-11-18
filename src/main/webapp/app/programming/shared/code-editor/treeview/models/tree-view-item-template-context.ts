import { TreeViewItem } from './tree-view-item';

export interface TreeViewItemTemplateContext<T> {
    item: TreeViewItem<T>;
    onCollapseExpand: () => void;
    onCheckedChange: () => void;
}
