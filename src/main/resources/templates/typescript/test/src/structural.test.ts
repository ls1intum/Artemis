import MergeSort from 'artemis-exercise/mergesort';
import BubbleSort from 'artemis-exercise/bubblesort';
import Context from 'artemis-exercise/context';
import Policy from 'artemis-exercise/policy';

// incorrect type structure should fail with runtime errors
const _MergeSort: any = MergeSort;
const _BubbleSort: any = BubbleSort;
const _Context: any = Context;
const _Policy: any = Policy;

describe('structural', () => {
    describe('Context', () => {
        const context = new _Context();

        it('has_properties', () => {
            expect(context).toHaveProperty('dates');
            expect(context).toHaveProperty('sortAlgorithm');
        });

        it('has_methods', () => {
            expect(context).toHaveProperty('sort', expect.any(Function));
        });
    });

    describe('Policy', () => {
        const context = new _Context();
        const policy = new _Policy(context);

        it('has_properties', () => {
            expect(policy).toHaveProperty('context');
        });

        it('has_methods', () => {
            expect(policy).toHaveProperty('configure', expect.any(Function));
        });
    });

    describe('BubbleSort', () => {
        it('has_method', () => {
            expect(_BubbleSort.prototype).toHaveProperty('performSort', expect.any(Function));
        });
    });

    describe('MergeSort', () => {
        it('has_method', () => {
            expect(_MergeSort.prototype).toHaveProperty('performSort', expect.any(Function));
        });
    });
});
