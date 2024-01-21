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
});
