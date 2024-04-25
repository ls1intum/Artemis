import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';

describe('SearchFilterPipe', () => {
    let pipe: SearchFilterPipe;

    beforeEach(() => {
        pipe = new SearchFilterPipe();
    });

    it('should filter array of objects based on search term', () => {
        const items = [
            { name: 'Apple', category: 'Fruit' },
            { name: 'Carrot', category: 'Vegetable' },
            { name: 'Banana', category: 'Fruit' },
        ];
        const value = 'Fruit';
        const filtered = pipe.transform(items, ['category'], value);
        expect(filtered).toHaveLength(2);
        expect(filtered).toEqual(
            expect.arrayContaining([
                { name: 'Apple', category: 'Fruit' },
                { name: 'Banana', category: 'Fruit' },
            ]),
        );
    });

    it('should return an empty array if no items match the search term', () => {
        const items = [{ name: 'Apple', category: 'Fruit' }];
        const value = 'Vegetable';
        const filtered = pipe.transform(items, ['category'], value);
        expect(filtered).toHaveLength(0);
    });

    it('should handle empty or invalid input gracefully', () => {
        const items = [{ name: 'Apple', category: 'Fruit' }];
        expect(pipe.transform([], ['name'], 'apple')).toHaveLength(0);
        expect(pipe.transform(undefined, ['name'], 'apple')).toEqual([]);
        expect(pipe.transform(items, ['name'], ' ')).toEqual([]);
    });

    it('should filter ignoring case sensitivity', () => {
        const items = [{ name: 'apple', category: 'fruit' }];
        const value = 'Apple';
        const filtered = pipe.transform(items, ['name'], value);
        expect(filtered).toHaveLength(1);
    });

    it('should allow partial matches of the search term', () => {
        const items = [{ name: 'apple', category: 'fruit' }];
        const value = 'app';
        const filtered = pipe.transform(items, ['name'], value);
        expect(filtered).toHaveLength(1);
    });

    it('should allow for partial matches at the beginning, middle, and end of the search fields', () => {
        const items = [
            { name: 'Caramel Apple', category: 'Dessert' },
            { name: 'Apple Pie', category: 'Dessert' },
            { name: 'Banana Split', category: 'Dessert' },
        ];
        // Partial match at the beginning
        let value = 'Car';
        let filtered = pipe.transform(items, ['name'], value);
        expect(filtered).toEqual(expect.arrayContaining([{ name: 'Caramel Apple', category: 'Dessert' }]));

        // Partial match in the middle
        value = 'pple P';
        filtered = pipe.transform(items, ['name'], value);
        expect(filtered).toEqual(expect.arrayContaining([{ name: 'Apple Pie', category: 'Dessert' }]));

        // Partial match at the end
        value = 'Split';
        filtered = pipe.transform(items, ['name'], value);
        expect(filtered).toEqual(expect.arrayContaining([{ name: 'Banana Split', category: 'Dessert' }]));
    });

    it('should filter array of objects based on multiple search fields', () => {
        const items = [
            { name: 'Apple', category: 'Fruit', color: 'Red' },
            { name: 'Carrot', category: 'Vegetable', color: 'Orange' },
            { name: 'Banana', category: 'Fruit', color: 'Yellow' },
            { name: 'Strawberry', category: 'Fruit', color: 'Red' },
        ];
        const searchTerm = 'Red';
        const filtered = pipe.transform(items, ['category', 'color'], searchTerm);
        expect(filtered).toHaveLength(2);
        expect(filtered).toEqual(
            expect.arrayContaining([
                { name: 'Apple', category: 'Fruit', color: 'Red' },
                { name: 'Strawberry', category: 'Fruit', color: 'Red' },
            ]),
        );
    });
});
