import { TreeviewPipe } from 'app/exercises/programming/shared/code-editor/treeview/pipes/treeview.pipe';

describe('TreeviewPipe', () => {
    const pipe = new TreeviewPipe();

    it('transforms null or undefined to undefined', () => {
        // @ts-ignore
        expect(pipe.transform(null, undefined)).toBe(undefined, 'case of null');
        // @ts-ignore
        expect(pipe.transform(undefined, undefined)).toBe(undefined, 'case of undefined');
    });

    it('transforms a list of objects to list of TreeItem objects', () => {
        const objects: any[] = [
            {
                name: 'leo',
                age: '18',
            },
            {
                name: 'vo',
                age: '14',
            },
        ];

        const treeItems = pipe.transform(objects, 'name')!;
        expect(objects.length === treeItems.length).toBe(true);
        expect(objects[0].name === treeItems[0].text && objects[1].name === treeItems[1].text).toBe(true);
        expect(objects[0] === treeItems[0].value && objects[1] === treeItems[1].value).toBe(true);
    });
});
