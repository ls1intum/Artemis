import { TestBed } from '@angular/core/testing';
import { SortService } from 'app/shared/service/sort.service';
import dayjs from 'dayjs/esm';

type TestObject = {
    a: number;
    b: string;
    c: dayjs.Dayjs;
    d: number | null | undefined;
    e: Map<string, number>;
};

describe('Sort Service', () => {
    let service: SortService;
    let e1: TestObject, e2: TestObject, e3: TestObject, e4: TestObject, e5: TestObject, e6: TestObject;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(SortService);

        e1 = {
            a: 10,
            b: 'dog',
            c: dayjs().subtract(1, 'days'),
            d: 2,
            e: new Map().set('f', 4),
        };
        e2 = {
            a: 18,
            b: 'cat',
            c: dayjs().subtract(20, 'hours'),
            d: 5,
            e: new Map().set('f', 8),
        };
        e3 = {
            a: 4,
            b: 'snake',
            c: dayjs().add(3, 'minutes'),
            d: null,
            e: new Map().set('f', 29),
        };
        e4 = {
            a: 28,
            b: 'panda',
            c: dayjs().subtract(4, 'years'),
            d: 1,
            e: new Map().set('f', 43),
        };
        e5 = {
            a: 15,
            b: 'giraffe',
            c: dayjs().add(2, 'hours'),
            d: 4,
            e: new Map().set('f', 6),
        };
        e6 = {
            a: 7,
            b: 'tiger',
            c: dayjs().subtract(5, 'minutes'),
            d: undefined,
            e: new Map().set('f', 16),
        };
    });

    describe('Service methods', () => {
        it(
            'should sort basic array ascending',
            repeatWithRandomArray(10, (arr) => {
                service.sortByProperty(arr, 'a', true);
                expect(arr).toEqual([e3, e6, e1, e5, e2, e4]);
            }),
        );

        it(
            'should sort basic array descending',
            repeatWithRandomArray(10, (arr) => {
                service.sortByProperty(arr, 'b', false);
                expect(arr).toEqual([e6, e3, e4, e5, e1, e2]);
            }),
        );

        it(
            'should sort array with dayjs properties',
            repeatWithRandomArray(10, (arr) => {
                service.sortByProperty(arr, 'c', true);
                expect(arr).toEqual([e4, e1, e2, e6, e3, e5]);
            }),
        );

        it(
            'should sort array with null properties ascending',
            repeatWithRandomArray(10, (arr) => {
                service.sortByProperty(arr, 'd', true);
                expect(arr.slice(0, 4)).toEqual([e4, e1, e5, e2]);
                expect(arr.slice(4, 6)).toIncludeSameMembers([e3, e6]);
            }),
        );

        it(
            'should sort array with null properties descending',
            repeatWithRandomArray(10, (arr) => {
                service.sortByProperty(arr, 'd', false);
                expect(arr).toHaveLength(6);
                expect(arr.slice(0, 2)).toIncludeSameMembers([e3, e6]);
                expect(arr.slice(2, 6)).toEqual([e2, e5, e1, e4]);
            }),
        );

        it(
            'should sort array of nested maps',
            repeatWithRandomArray(10, (arr) => {
                service.sortByProperty(arr, 'e.f', true);
                expect(arr).toEqual([e1, e5, e2, e6, e3, e4]);
            }),
        );

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
