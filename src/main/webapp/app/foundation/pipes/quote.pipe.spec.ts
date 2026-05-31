import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it } from 'vitest';
import { QuotePipe } from 'app/foundation/pipes/quote.pipe';

describe('QuotePipe', () => {
    setupTestBed({ zoneless: true });

    let pipe: QuotePipe;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [QuotePipe],
        })
            .compileComponents()
            .then(() => {
                pipe = new QuotePipe();
            });
    });

    it.each([undefined, ''])('should return an empty string if given nothing', (text) => {
        expect(pipe.transform(text)).toBe('');
    });

    it('should wrap the text in quoted if non-empty', () => {
        expect(pipe.transform('input')).toBe('"input"');
    });

    it('should not add the prefix in the empty case', () => {
        expect(pipe.transform('', 'prefix')).toBe('');
    });

    it('should add a prefix if specified', () => {
        expect(pipe.transform('content', 'prefix')).toBe('prefix"content"');
    });
});
