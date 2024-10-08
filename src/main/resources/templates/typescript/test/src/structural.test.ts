import MergeSort from 'artemis-exercise/mergesort';
import BubbleSort from 'artemis-exercise/bubblesort';
import Context from 'artemis-exercise/context';
import Policy from 'artemis-exercise/policy';

describe('structural', () => {
    describe('Context', () => {
        const context = new Context();

        it('has_properties', () => {
            expect(context).toHaveProperty('dates');
            expect(context).toHaveProperty('sortAlgorithm');
        });

        it('has_methods', () => {
            expect(context).toHaveProperty('sort', expect.any(Function));
        });
    });

    describe('Policy', () => {
        const context = new Context();
        const policy = new Policy(context);

        it('has_properties', () => {
            expect(policy).toHaveProperty('context');
        });

        it('has_methods', () => {
            expect(policy).toHaveProperty('configure', expect.any(Function));
        });
    });

    describe('BubbleSort', () => {
        it('has_method', () => {
            expect(BubbleSort.prototype).toHaveProperty('performSort', expect.any(Function));
        });
    });

    describe('MergeSort', () => {
        it('has_method', () => {
            expect(MergeSort.prototype).toHaveProperty('performSort', expect.any(Function));
        });
    });
});
