import { TreeViewItem } from 'app/programming/shared/code-editor/treeview/models/tree-view-item';

describe('TreeviewItem', () => {
    it('should throw error if TreeItem param is null of undefined', () => {
        const error = new Error('Item must be defined');
        // @ts-ignore
        expect(() => new TreeViewItem(null)).toThrow(error);
        // @ts-ignore
        expect(() => new TreeViewItem(undefined)).toThrow(error);
    });

    it('should throw error if TreeItem text is not a string', () => {
        const error = new Error('A text of item must be string object');
        const fakeString: any = 1;
        // @ts-ignore
        expect(() => new TreeViewItem<number>({ text: null, value: 1 })).toThrow(error);
        // @ts-ignore
        expect(() => new TreeViewItem<number>({ text: undefined, value: 1 })).toThrow(error);
        expect(() => new TreeViewItem<number>({ children: [], text: fakeString, value: 1 })).toThrow(error);
    });

    it('should allow to create TreeviewItem with empty children', () => {
        const treeviewItem = new TreeViewItem<number>({ text: 'Parent', value: 1, children: [] });
        expect(treeviewItem.children).toEqual([]);
    });

    describe('collapsed', () => {
        it('should set value is false by default', () => {
            const treeviewItem = new TreeViewItem<number>({ children: [], text: 'Parent', value: 1 });
            expect(treeviewItem.collapsed).toBeFalsy();
        });

        it('should affectedly change collapsed value', () => {
            const treeviewItem = new TreeViewItem<number>({ children: [], text: 'Parent', value: 1, collapsed: true });
            expect(treeviewItem.collapsed).toBeTruthy();
            treeviewItem.collapsed = false;
            expect(treeviewItem.collapsed).toBeFalsy();
            treeviewItem.collapsed = false;
            expect(treeviewItem.collapsed).toBeFalsy();
        });
    });

    describe('setCollapsedRecursive', () => {
        it('should apply collapsed value to children', () => {
            const treeviewItem = new TreeViewItem({
                text: 'Parent',
                value: 1,
                collapsed: false,
                children: [{ text: 'Child 1', value: 11, collapsed: false } as TreeViewItem<number>],
            });
            expect(treeviewItem.children[0].collapsed).toBeFalse();
            treeviewItem.setCollapsedRecursive(true);
            expect(treeviewItem.children[0].collapsed).toBeTrue();
        });
    });

    describe('disabled', () => {
        it('should set value is false by default', () => {
            const treeviewItem = new TreeViewItem<number>({ children: [], text: 'Parent', value: 1 });
            expect(treeviewItem.disabled).toBeFalsy();
        });

        it('should initialize children are disabled if initializing parent is disabled', () => {
            const treeviewItem = new TreeViewItem({
                text: 'Parent',
                value: 1,
                disabled: true,
                children: [{ text: 'Child', value: 11, disabled: false } as TreeViewItem<number>],
            });
            expect(treeviewItem.children[0].disabled).toBeTruthy();
        });

        it('should change disabled value of children to false if changing disabled of parent to false', () => {
            const treeviewItem = new TreeViewItem({
                text: 'Parent',
                value: 1,
                children: [{ text: 'Child 1', value: 11 } as TreeViewItem<number>],
            });
            expect(treeviewItem.children[0].disabled).toBeFalse();
            treeviewItem.disabled = true;
            expect(treeviewItem.children[0].disabled).toBeTrue();
            treeviewItem.disabled = true;
            expect(treeviewItem.children[0].disabled).toBeTrue();
        });
    });

    describe('children', () => {
        it('should affectedly change children value', () => {
            const treeviewItem = new TreeViewItem<number>({ children: [], text: 'Parent', value: 1 });
            const children: TreeViewItem<number>[] = [new TreeViewItem<number>({ children: [], text: 'Child 1', value: 11 })];
            expect(treeviewItem.children).toEqual([]);
            treeviewItem.children = children;
            expect(treeviewItem.children).toBe(children);
            treeviewItem.children = children;
            expect(treeviewItem.children).toBe(children);
        });

        it('should accept undefined value', () => {
            const treeviewItem = new TreeViewItem({
                text: 'Parent',
                value: 1,
                children: [{ text: 'Child 1', value: 11 } as TreeViewItem<number>],
            });
            expect(treeviewItem.children).toBeDefined();
            // @ts-ignore
            treeviewItem.children = undefined;
            expect(treeviewItem.children).toBeUndefined();
        });
    });
});
