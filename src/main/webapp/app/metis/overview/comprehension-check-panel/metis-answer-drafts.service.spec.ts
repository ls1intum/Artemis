import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { MetisAnswerDrafts } from 'app/metis/overview/comprehension-check-panel/metis-answer-drafts.service';

describe('MetisAnswerDrafts', () => {
    let drafts: MetisAnswerDrafts;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        drafts = TestBed.inject(MetisAnswerDrafts);
    });

    it('should give back the draft of the open question of an exercise', () => {
        drafts.keep(7, 1, 'The inner loop stops once');
        drafts.keep(8, 3, 'Another exercise');

        expect(drafts.restore(7, 1)).toBe('The inner loop stops once');
        expect(drafts.restore(8, 3)).toBe('Another exercise');
        // Restoring does not use it up: the panel may be created anew more than once while the question is open
        expect(drafts.restore(7, 1)).toBe('The inner loop stops once');
    });

    it('should keep only the latest draft of an exercise', () => {
        drafts.keep(7, 1, 'The inner loop');
        drafts.keep(7, 1, 'The inner loop stops once');

        expect(drafts.restore(7, 1)).toBe('The inner loop stops once');
    });

    it('should give no draft back for another question, and drop the one kept for the question that is no longer open', () => {
        drafts.keep(7, 1, 'About the first question');

        expect(drafts.restore(7, 2)).toBe('');
        expect(drafts.restore(7, 1)).toBe('');
    });

    it('should keep nothing for an empty draft', () => {
        drafts.keep(7, 1, 'Typed and deleted again');
        drafts.keep(7, 1, '');

        expect(drafts.restore(7, 1)).toBe('');
    });

    it('should drop the draft of an exercise without touching the others', () => {
        drafts.keep(7, 1, 'About the first question');
        drafts.keep(8, 3, 'Another exercise');

        drafts.drop(7);

        expect(drafts.restore(7, 1)).toBe('');
        expect(drafts.restore(8, 3)).toBe('Another exercise');
    });
});
