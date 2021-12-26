import { TreeviewItem } from './treeview-item';

export interface TreeviewItemTemplateContext {
    item: TreeviewItem;
    onCollapseExpand: () => void;
    onCheckedChange: () => void;
}
