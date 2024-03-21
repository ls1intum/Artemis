import { TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { SortService } from 'app/shared/service/sort.service';

type TestObject = {
    a: number;
    b: string;
    c: dayjs.Dayjs;
    d: number | null | undefined;
    e: Map<string, number>;
    f: number;
    g: number;
    h: { i: number }[] | undefined;
    j: (() => number) | undefined;
};

describe('Sort Service', () => {
    let service: SortService;
    const e1: TestObject = {
        a: 10,
        b: 'dog',
        c: dayjs().subtract(1, 'days'),
        d: 2,
        e: new Map().set('f', 4),
        f: 1,
        g: 3,
        h: [{ i: 1 }, { i: 2 }],
        j: () => 9,
    };
    const e2: TestObject = {
        a: 18,
        b: 'cat',
        c: dayjs().subtract(20, 'hours'),
        d: 5,
        e: new Map().set('f', 8),
        f: 1,
        g: 2,
        h: [],
        j: () => 3,
    };
    const e3: TestObject = {
        a: 4,
        b: 'snake',
        c: dayjs().add(3, 'minutes'),
        d: null,
        e: new Map().set('f', 29),
        f: 1,
        g: 1,
        h: undefined,
        j: () => 8,
    };
    const e4: TestObject = {
        a: 28,
        b: 'panda',
        c: dayjs().subtract(4, 'years'),
        d: 1,
        e: new Map().set('f', 43),
        f: 4,
        g: 4,
        h: [{ i: 3 }],
        j: undefined,
    };
    const e5: TestObject = {
        a: 15,
        b: 'giraffe',
        c: dayjs().add(2, 'hours'),
        d: 4,
        e: new Map().set('f', 6),
        f: 5,
        g: 5,
        h: [{ i: 4 }, { i: -1 }],
        j: () => 10,
    };
    const e6: TestObject = {
        a: 7,
        b: 'tiger',
        c: dayjs().subtract(5, 'minutes'),
        d: undefined,
        e: new Map().set('f', 16),
        f: 6,
        g: 6,
        h: [{ i: 0 }],
        j: undefined,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(SortService);
    });

    describe('Service methods', () => {
        it.each([
            ['a', true, [e3, e6, e1, e5, e2, e4]],
            ['b', false, [e6, e3, e4, e5, e1, e2]],
            ['c', true, [e4, e1, e2, e6, e3, e5]],
            ['e.f', true, [e1, e5, e2, e6, e3, e4]],
        ])('should sort basic array', (key: string, ascending: boolean, expectedOrder: TestObject[]) => {
            let times = 10;
            while (times-- > 0) {
                const shuffled = shuffle([e1, e2, e3, e4, e5, e6]);
                service.sortByProperty(shuffled, key, ascending);
                expect(shuffled).toEqual(expectedOrder);
            }
        });

        it.each([
            ['d', true, [e4, e1, e5, e2], [e3, e6]],
            ['d', false, [e2, e5, e1, e4], [e3, e6]],
            ['h[0]?.i', true, [e6, e1, e4, e5], [e2, e3]],
            ['h[0]?.i', false, [e5, e4, e1, e6], [e2, e3]],
            ['h.last()?.i', true, [e5, e6, e1, e4], [e2, e3]],
            ['h.last()?.i', false, [e4, e1, e6, e5], [e2, e3]],
            ['j()', true, [e2, e3, e1, e5], [e4, e6]],
            ['j()', false, [e5, e1, e3, e2], [e4, e6]],
        ])('should sort basic array with null values', (key: string, ascending: boolean, expectedDefinedOrder: TestObject[], undefinedObjects: TestObject[]) => {
            let times = 10;
            while (times-- > 0) {
                const shuffled = shuffle([e1, e2, e3, e4, e5, e6]);
                service.sortByProperty(shuffled, key, ascending);
                if (ascending) {
                    expect(shuffled.slice(0, expectedDefinedOrder.length)).toEqual(expectedDefinedOrder);
                    expect(shuffled.slice(expectedDefinedOrder.length, shuffled.length)).toIncludeSameMembers(undefinedObjects);
                } else {
                    expect(shuffled.slice(0, undefinedObjects.length)).toIncludeSameMembers(undefinedObjects);
                    expect(shuffled.slice(undefinedObjects.length, shuffled.length)).toEqual(expectedDefinedOrder);
                }
            }
        });

        it.each([
            [['f', 'g'], true, [e3, e2, e1, e4, e5, e6]],
            [['f', 'g'], false, [e6, e5, e4, e1, e2, e3]],
        ])('should sort array by multiple values', (keys: string[], ascending: boolean, expectedOrder: TestObject[]) => {
            let times = 10;
            while (times-- > 0) {
                const shuffled = shuffle([e1, e2, e3, e4, e5, e6]);
                service.sortByMultipleProperties(shuffled, keys, ascending);
                expect(shuffled).toEqual(expectedOrder);
            }
        });

        it(
            'should sort array using a function for the compare value',
            repeatWithRandomArray(10, (arr) => {
                service.sortByFunction(arr, (element) => 2 * element.a - 5, true);
                expect(arr).toEqual([e3, e6, e1, e5, e2, e4]);
            }),
        );
    });

    function repeatWithRandomArray(times: number, fn: (arr: TestObject[]) => void) {
        return () => {
            while (times-- > 0) {
                fn(shuffle([e1, e2, e3, e4, e5, e6]));
            }
        };
    }

    function shuffle(array: TestObject[]) {
        return array.sort(() => Math.random() - 0.5);
    }
});
