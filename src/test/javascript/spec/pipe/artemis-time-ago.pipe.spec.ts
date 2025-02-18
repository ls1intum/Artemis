import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ChangeDetectorRef } from '@angular/core';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { provideHttpClient } from '@angular/common/http';

describe('ArtemisTimeAgoPipe', () => {
    let pipe: ArtemisTimeAgoPipe;
    let translateService: TranslateService;
    const cdRef = { markForCheck: jest.fn() } as any as ChangeDetectorRef;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [ArtemisTimeAgoPipe, { provide: ChangeDetectorRef, useValue: cdRef }, { provide: TranslateService, useClass: MockTranslateService }, provideHttpClient()],
        }).compileComponents();
        translateService = TestBed.inject(TranslateService);
        translateService.use('en');
        pipe = TestBed.inject(ArtemisTimeAgoPipe);
    });

    it.each([
        { value: dayjs().add(5, 'minutes'), expected: { en: 'in 5 minutes', de: 'in 5 Minuten' } },
        { value: dayjs().add(10, 'minutes'), expected: { en: 'in 10 minutes', de: 'in 10 Minuten' } },
        { value: dayjs().add(2, 'hours'), expected: { en: 'in 2 hours', de: 'in 2 Stunden' } },
        { value: dayjs().subtract(5, 'minutes'), expected: { en: '5 minutes ago', de: 'vor 5 Minuten' } },
        { value: dayjs().subtract(10, 'minutes'), expected: { en: '10 minutes ago', de: 'vor 10 Minuten' } },
        { value: dayjs().subtract(2, 'hours'), expected: { en: '2 hours ago', de: 'vor 2 Stunden' } },
    ])('returns the correct time and switches the language correctly', ({ value, expected }) => {
        Object.entries(expected).forEach(([langKey, expectedOutput]) => {
            translateService.use(langKey);
            expect(pipe.transform(value)).toBe(expectedOutput);
        });
    });

    it('updates the output automatically as time passes, but removes the timer after destroy', fakeAsync(() => {
        const time = dayjs().subtract(60, 'seconds');
        expect(pipe.transform(time)).toBe('a minute ago');
        expect(cdRef.markForCheck).not.toHaveBeenCalled();
        tick(30000);
        expect(cdRef.markForCheck).toHaveBeenCalledOnce();
        expect(pipe.transform(time)).toBe('2 minutes ago');

        pipe.ngOnDestroy();
        tick(30000);
        expect(cdRef.markForCheck).toHaveBeenCalledOnce(); // not a second time
    }));
});
