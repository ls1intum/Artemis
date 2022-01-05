import { TreeviewItem } from './treeview-item';

export interface TreeviewItemTemplateContext<T> {
    item: TreeviewItem<T>;
    onCollapseExpand: () => void;
    onCheckedChange: () => void;
}
