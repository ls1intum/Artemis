import MergeSort from 'artemis-exercise/mergesort.js';
import BubbleSort from 'artemis-exercise/bubblesort.js';
import Context from 'artemis-exercise/context.js';
import Policy from 'artemis-exercise/policy.js';

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
