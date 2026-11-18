import MergeSort from 'artemis-exercise/mergesort.js';
import BubbleSort from 'artemis-exercise/bubblesort.js';
import Context from 'artemis-exercise/context.js';
import Policy from 'artemis-exercise/policy.js';

// prettier-ignore
const datesWithCorrectOrder = [
    new Date('2016-02-15'),
    new Date('2017-04-15'),
    new Date('2017-09-15'),
    new Date('2018-11-08'),
];

describe('behavior', () => {
    let dates;
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
            const bubbleSort = new BubbleSort();
            bubbleSort.performSort(dates);
            expect(dates).toEqual(datesWithCorrectOrder);
        });
    });

    describe('MergeSort', () => {
        it('should_sort_correctly', () => {
            const mergeSort = new MergeSort();
            mergeSort.performSort(dates);
            expect(dates).toEqual(datesWithCorrectOrder);
        });
    });

    describe('Policy', () => {
        it('uses_MergeSort_for_big_list', () => {
            const bigList = [];
            for (let i = 0; i < 11; i++) {
                bigList.push(new Date());
            }

            const context = new Context();
            context.dates = bigList;
            const policy = new Policy(context);
            policy.configure();
            const chosenSortStrategy = context.sortAlgorithm;
            expect(chosenSortStrategy).toBeInstanceOf(MergeSort);
        });

        it('uses_BubbleSort_for_small_list', () => {
            const smallList = [];
            for (let i = 0; i < 3; i++) {
                smallList.push(new Date());
            }

            const context = new Context();
            context.dates = smallList;
            const policy = new Policy(context);
            policy.configure();
            const chosenSortStrategy = context.sortAlgorithm;
            expect(chosenSortStrategy).toBeInstanceOf(BubbleSort);
        });
    });
});
