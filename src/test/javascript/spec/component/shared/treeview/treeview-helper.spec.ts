import { TreeviewItem } from 'app/exercises/programming/shared/code-editor/treeview/models/treeview-item';
import { findItem, findItemInList } from 'app/exercises/programming/shared/code-editor/treeview/helpers/treeview-helper';

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

describe('findItem', () => {
    it('should not find item if root is undefined', () => {
        expect(findItem(undefined, 1)).toBeUndefined();
    });

    it('should find item', () => {
        expect(findItem(rootNoChildren, 1)).toEqual(rootNoChildren);
        expect(findItem(rootHasChildren, 2)).toEqual(rootHasChildren.children![0]);
    });

    it('should not find item', () => {
        expect(findItem(rootNoChildren, 2)).toBeUndefined();
        expect(findItem(rootHasChildren, 0)).toBeUndefined();
    });
});

describe('findItemInList', () => {
    it('should not find item if list is undefined', () => {
        expect(findItemInList(undefined, 1)).toBeUndefined();
    });

    it('should find item', () => {
        const list = [rootNoChildren, rootHasChildren];
        expect(findItemInList(list, 2)).toEqual(rootHasChildren.children![0]);
    });

    it('should not find item', () => {
        const list = [rootNoChildren, rootHasChildren];
        expect(findItemInList(list, 0)).toBeUndefined();
    });
});
