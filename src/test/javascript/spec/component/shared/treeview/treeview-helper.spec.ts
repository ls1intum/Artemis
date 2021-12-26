import { cloneDeep } from 'lodash';
import { TreeviewItem } from 'app/exercises/programming/shared/code-editor/treeview/models/treeview-item';
import { TreeviewHelper } from 'app/exercises/programming/shared/code-editor/treeview/helpers/treeview-helper';

const rootNoChildren = new TreeviewItem({
    text: '1',
    value: 1,
});
const rootHasChildren = new TreeviewItem({
    text: '1',
    value: 1,
    checked: false,
    children: [
        {
            text: '2',
            value: 2,
            checked: true,
        },
        {
            text: '3',
            value: 3,
            checked: false,
            children: [
                {
                    text: '4',
                    value: 4,
                    checked: false,
                },
            ],
        },
    ],
});
const fakeItem = new TreeviewItem({
    text: '1',
    value: 1,
});

describe('findItem', () => {
    it('should not find item if root is undefined', () => {
        expect(TreeviewHelper.findItem(undefined, 1)).toBeUndefined();
    });

    it('should find item', () => {
        expect(TreeviewHelper.findItem(rootNoChildren, 1)).toEqual(rootNoChildren);
        expect(TreeviewHelper.findItem(rootHasChildren, 2)).toEqual(rootHasChildren.children![0]);
    });

    it('should not find item', () => {
        expect(TreeviewHelper.findItem(rootNoChildren, 2)).toBeUndefined();
        expect(TreeviewHelper.findItem(rootHasChildren, 0)).toBeUndefined();
    });
});

describe('findItemInList', () => {
    it('should not find item if list is undefined', () => {
        expect(TreeviewHelper.findItemInList(undefined, 1)).toBeUndefined();
    });

    it('should find item', () => {
        const list = [rootNoChildren, rootHasChildren];
        expect(TreeviewHelper.findItemInList(list, 2)).toEqual(rootHasChildren.children![0]);
    });

    it('should not find item', () => {
        const list = [rootNoChildren, rootHasChildren];
        expect(TreeviewHelper.findItemInList(list, 0)).toBeUndefined();
    });
});

describe('findParent', () => {
    it('should not find parent if root is undefined or root has no children', () => {
        expect(TreeviewHelper.findParent(undefined, fakeItem)).toBeUndefined();
        expect(TreeviewHelper.findParent(rootNoChildren, fakeItem)).toBeUndefined();
    });

    it('should not find parent', () => {
        expect(TreeviewHelper.findParent(rootHasChildren, fakeItem)).toBeUndefined();
    });

    it('should find parent', () => {
        let parent = rootHasChildren;
        let item = rootHasChildren.children![0];
        expect(TreeviewHelper.findParent(rootHasChildren, item)).toBe(parent);

        parent = rootHasChildren.children![1];
        item = rootHasChildren.children![1].children![0];
        expect(TreeviewHelper.findParent(rootHasChildren, item)).toBe(parent);
    });
});

describe('removeParent', () => {
    it('should not remove item if root is undefined or root has no children', () => {
        expect(TreeviewHelper.removeItem(undefined, fakeItem)).toBeFalsy();
        expect(TreeviewHelper.removeItem(rootNoChildren, fakeItem)).toBeFalsy();
    });

    it('should not remove item', () => {
        expect(TreeviewHelper.removeItem(rootHasChildren, fakeItem)).toBeFalsy();
    });

    it('should remove item & has correct checked', () => {
        const root = cloneDeep(rootHasChildren);
        const parent = root;
        let item = root.children[1];
        expect(parent.children.length).toBe(2);
        expect(parent.checked).toBe(undefined);
        expect(TreeviewHelper.removeItem(parent, item)).toBe(true);
        expect(parent.children.length).toBe(1);
        expect(parent.checked).toBe(true);

        item = root.children[0];
        expect(TreeviewHelper.removeItem(parent, item)).toBe(true);
        expect(parent.children).toBeUndefined();
    });
});
