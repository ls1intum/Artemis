import MergeSort from 'artemis-exercise/mergesort';
import BubbleSort from 'artemis-exercise/bubblesort';
import Context from 'artemis-exercise/context';
import Policy from 'artemis-exercise/policy';

// incorrect type structure should fail with runtime errors
const _MergeSort: any = MergeSort;
const _BubbleSort: any = BubbleSort;
const _Context: any = Context;
const _Policy: any = Policy;

// prettier-ignore
const datesWithCorrectOrder = [
    new Date('2016-02-15'),
    new Date('2017-04-15'),
    new Date('2017-09-15'),
    new Date('2018-11-08'),
];

describe('behavior', () => {
    let dates: Array<Date>;
    beforeEach(() => {
        // prettier-ignore
        dates = [
            new Date('2018-11-08'),
            new Date('2017-04-15'),
            new Date('2016-02-15'),
            new Date('2017-09-15'),
        ];
    });

    describe('BubbleSort', () => {
        it('should_sort_correctly', () => {
            const bubbleSort = new _BubbleSort();
            bubbleSort.performSort(dates);
            expect(dates).toEqual(datesWithCorrectOrder);
        });
    });

    describe('MergeSort', () => {
        it('should_sort_correctly', () => {
            const mergeSort = new _MergeSort();
            mergeSort.performSort(dates);
            expect(dates).toEqual(datesWithCorrectOrder);
        });
    });

    describe('Policy', () => {
        it('uses_MergeSort_for_big_list', () => {
            const bigList: Array<Date> = [];
            for (let i = 0; i < 11; i++) {
                bigList.push(new Date());
            }

            const context = new _Context();
            context.dates = bigList;
            const policy = new _Policy(context);
            policy.configure();
            const chosenSortStrategy = context.sortAlgorithm;
            expect(chosenSortStrategy).toBeInstanceOf(_MergeSort);
        });

        it('uses_BubbleSort_for_small_list', () => {
            const smallList: Array<Date> = [];
            for (let i = 0; i < 3; i++) {
                smallList.push(new Date());
            }

            const context = new _Context();
            context.dates = smallList;
            const policy = new _Policy(context);
            policy.configure();
            const chosenSortStrategy = context.sortAlgorithm;
            expect(chosenSortStrategy).toBeInstanceOf(_BubbleSort);
        });
    });
});
