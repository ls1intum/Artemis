import { TreeviewItem } from './treeview-item';
import { TreeviewConfig } from './treeview-config';

export interface TreeviewHeaderTemplateContext {
    config: TreeviewConfig;
    item: TreeviewItem;
    onCollapseExpand: () => void;
    onCheckedChange: (checked: boolean) => void;
    onFilterTextChange: (text: string) => void;
}
