import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TumUiTranslatePipe } from './tum-ui-translate.pipe';
import { TUM_UI_TRANSLATOR, TumUiTranslator } from './tum-ui-translations';

describe('TumUiTranslatePipe', () => {
    it('provides usable English defaults with parameter interpolation', () => {
        TestBed.configureTestingModule({ providers: [TumUiTranslatePipe] });

        const pipe = TestBed.inject(TumUiTranslatePipe);

        expect(pipe.transform('tumUi.paginator.currentPageReport', { first: 1, second: 10, total: 42 })).toBe('Showing 1 to 10 of 42');
        expect(pipe.transform('consumer.owned.key')).toBe('consumer.owned.key');
        expect(pipe.transform(undefined)).toBe('');
    });

    it('delegates to a host translator and observes its invalidation signal', () => {
        const revision = signal(0);
        const translator: TumUiTranslator = {
            changes: revision,
            translate: vi.fn((key, params) => `${key}:${params?.name}`),
        };
        TestBed.configureTestingModule({
            providers: [TumUiTranslatePipe, { provide: TUM_UI_TRANSLATOR, useValue: translator }],
        });

        const pipe = TestBed.inject(TumUiTranslatePipe);
        expect(pipe.transform('welcome', { name: 'Artemis' })).toBe('welcome:Artemis');

        revision.update((value) => value + 1);
        expect(pipe.transform('welcome', { name: 'TUM' })).toBe('welcome:TUM');
        expect(translator.translate).toHaveBeenCalledTimes(2);
    });
});
