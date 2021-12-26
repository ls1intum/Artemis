import { TestBed } from '@angular/core/testing';
import { TreeviewItem, TreeviewSelection } from 'app/exercises/programming/shared/code-editor/treeview/models/treeview-item';
import { TreeviewComponent } from 'app/exercises/programming/shared/code-editor/treeview/components/treeview/treeview.component';
import { TreeviewModule } from 'app/exercises/programming/shared/code-editor/treeview/treeview.module';
import {
    TreeviewEventParser,
    DefaultTreeviewEventParser,
    DownlineTreeviewEventParser,
    OrderDownlineTreeviewEventParser,
} from 'app/exercises/programming/shared/code-editor/treeview/helpers/treeview-event-parser';

const selectionWithUndefinedCheckedItems: TreeviewSelection = {
    // @ts-ignore
    checkedItems: undefined,
    // @ts-ignore
    uncheckedItems: undefined,
};

const selectionWithNullCheckedItems: TreeviewSelection = {
    // @ts-ignore
    checkedItems: null,
    // @ts-ignore
    uncheckedItems: undefined,
};

describe('DefaultTreeviewEventParser', () => {
    let parser: TreeviewEventParser;
    let fakeComponent: TreeviewComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TreeviewModule.forRoot()],
            providers: [{ provide: TreeviewEventParser, useClass: DefaultTreeviewEventParser }],
        });
        parser = TestBed.inject(TreeviewEventParser);
        fakeComponent = TestBed.createComponent(TreeviewComponent).componentInstance;
    });

    it('should return empty list if checkedItems is null or undefined', () => {
        // @ts-ignore
        fakeComponent.items = undefined;
        fakeComponent.selection = selectionWithUndefinedCheckedItems;
        let result = parser.getSelectedChange(fakeComponent);
        expect(result).toEqual([]);

        // @ts-ignore
        fakeComponent.items = undefined;
        fakeComponent.selection = selectionWithNullCheckedItems;
        result = parser.getSelectedChange(fakeComponent);
        expect(result).toEqual([]);
    });

    it('should return list of value of checked items', () => {
        // @ts-ignore
        fakeComponent.items = undefined;
        fakeComponent.selection = {
            checkedItems: [new TreeviewItem({ text: 'Item1', value: 1 }), new TreeviewItem({ text: 'Item2', value: 2 })],
            // @ts-ignore
            uncheckedItems: undefined,
        };

        const result = parser.getSelectedChange(fakeComponent);
        expect(result).toEqual([1, 2]);
    });
});

describe('DownlineTreeviewEventParser', () => {
    let parser: TreeviewEventParser;
    let fakeComponent: TreeviewComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TreeviewModule.forRoot()],
            providers: [{ provide: TreeviewEventParser, useClass: DownlineTreeviewEventParser }],
        });
        parser = TestBed.inject(TreeviewEventParser);
        fakeComponent = TestBed.createComponent(TreeviewComponent).componentInstance;
    });

    it('should return empty list if items is null or undefined', () => {
        // @ts-ignore
        fakeComponent.items = undefined;
        fakeComponent.selection = selectionWithUndefinedCheckedItems;
        let result = parser.getSelectedChange(fakeComponent);
        expect(result).toEqual([]);

        // @ts-ignore
        fakeComponent.items = null;
        fakeComponent.selection = selectionWithUndefinedCheckedItems;
        result = parser.getSelectedChange(fakeComponent);
        expect(result).toEqual([]);
    });

    it('should return list of checked items with links', () => {
        const item1 = new TreeviewItem({ text: 'Item1', value: 1, checked: false });
        const item1Child1 = new TreeviewItem({ text: 'Item11', value: 11 });
        const item1Child2 = new TreeviewItem({
            text: 'Item12',
            value: 12,
            checked: false,
            children: [{ text: 'Item12', value: 12, checked: false }],
        });
        item1.children = [item1Child1, item1Child2];
        const item2 = new TreeviewItem({ text: 'Item2', value: 2 });
        const item3 = new TreeviewItem({ text: 'Item3', value: 3, checked: false });
        fakeComponent.items = [item1, item2, item3];
        fakeComponent.selection = selectionWithUndefinedCheckedItems;
        const result = parser.getSelectedChange(fakeComponent);
        const expected = [
            {
                item: item1Child1,
                parent: {
                    item: item1,
                    parent: null,
                },
            },
            {
                item: item2,
                parent: null,
            },
        ];
        expect(result).toEqual(expected);
    });
});

describe('OrderDownlineTreeviewEventParser', () => {
    let parser: TreeviewEventParser;
    let fakeComponent: TreeviewComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TreeviewModule.forRoot()],
            providers: [{ provide: TreeviewEventParser, useClass: OrderDownlineTreeviewEventParser }],
        });
        parser = TestBed.inject(TreeviewEventParser);
        fakeComponent = TestBed.createComponent(TreeviewComponent).componentInstance;
    });

    it('should return empty list if items is null or undefined', () => {
        // @ts-ignore
        fakeComponent.items = undefined;
        fakeComponent.selection = selectionWithUndefinedCheckedItems;
        let result = parser.getSelectedChange(fakeComponent);
        expect(result).toEqual([]);

        // @ts-ignore
        fakeComponent.items = null;
        fakeComponent.selection = selectionWithUndefinedCheckedItems;
        result = parser.getSelectedChange(fakeComponent);
        expect(result).toEqual([]);
    });

    describe('', () => {
        const item1 = new TreeviewItem({ text: 'Item1', value: 1, checked: false });
        const item1Child1 = new TreeviewItem({ text: 'Item11', value: 11 });
        const item1Child2 = new TreeviewItem({
            text: 'Item12',
            value: 12,
            checked: false,
            children: [{ text: 'Item12', value: 12, checked: false }],
        });
        item1.children = [item1Child1, item1Child2];
        const item2 = new TreeviewItem({ text: 'Item2', value: 2, checked: false });
        const item3 = new TreeviewItem({ text: 'Item3', value: 3 });

        beforeEach(() => {
            fakeComponent.items = [item1, item2, item3];
            fakeComponent.selection = selectionWithUndefinedCheckedItems;
        });

        it('should return list of checked items with links', () => {
            const result = parser.getSelectedChange(fakeComponent);
            const expected = [
                {
                    item: item1Child1,
                    parent: {
                        item: item1,
                        parent: null,
                    },
                },
                {
                    item: item3,
                    parent: null,
                },
            ];
            expect(result).toEqual(expected);
        });

        it('should return list of checked items with links by order', () => {
            parser.getSelectedChange(fakeComponent);
            item1Child1.checked = false;
            item2.checked = true;
            const result = parser.getSelectedChange(fakeComponent);
            const expected = [
                {
                    item: item3,
                    parent: null,
                },
                {
                    item: item2,
                    parent: null,
                },
            ];
            expect(result).toEqual(expected);
        });
    });
});
