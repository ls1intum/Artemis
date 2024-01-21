import { TestBed } from '@angular/core/testing';
import { QuotePipe } from 'app/shared/pipes/quote.pipe';

describe('QuotePipe', () => {
    let pipe: QuotePipe;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [QuotePipe],
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
