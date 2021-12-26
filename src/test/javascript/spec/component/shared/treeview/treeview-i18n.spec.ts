import { TestBed } from '@angular/core/testing';
import { TreeviewItem, TreeviewSelection } from 'app/exercises/programming/shared/code-editor/treeview/models/treeview-item';
import { DefaultTreeviewI18n, TreeviewI18n } from 'app/exercises/programming/shared/code-editor/treeview/models/treeview-i18n';

describe('DefaultTreeviewI18n', () => {
    let treeviewI18n: TreeviewI18n;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [DefaultTreeviewI18n],
        });
        treeviewI18n = TestBed.inject(DefaultTreeviewI18n);
    });

    describe('getText', () => {
        it('should return "All" if having no unchecked items', () => {
            const selection: TreeviewSelection = {
                // @ts-ignore
                checkedItems: undefined,
                uncheckedItems: [],
            };
            expect(treeviewI18n.getText(selection)).toBe('All');
        });

        it('should return "Select options" if list of checked items is empty', () => {
            const selection: TreeviewSelection = {
                checkedItems: [],
                uncheckedItems: [new TreeviewItem({ text: 'Item 1', value: undefined })],
            };
            expect(treeviewI18n.getText(selection)).toBe('Select options');
        });

        it('should return text of first checked item if length of checked items is 1', () => {
            const selection: TreeviewSelection = {
                checkedItems: [new TreeviewItem({ text: 'Item 1', value: undefined })],
                uncheckedItems: [new TreeviewItem({ text: 'Item 2', value: undefined })],
            };
            expect(treeviewI18n.getText(selection)).toBe('Item 1');
        });

        it('should return "2 options selected" if length of checked items is 2', () => {
            const selection: TreeviewSelection = {
                checkedItems: [new TreeviewItem({ text: 'Item 1', value: undefined }), new TreeviewItem({ text: 'Item 2', value: undefined })],
                uncheckedItems: [new TreeviewItem({ text: 'Item 3', value: undefined })],
            };
            expect(treeviewI18n.getText(selection)).toBe('2 options selected');
        });
    });
});
