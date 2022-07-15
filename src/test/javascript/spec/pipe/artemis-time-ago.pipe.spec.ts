import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ChangeDetectorRef, NgZone } from '@angular/core';
import dayjs from 'dayjs/esm';

describe('ArtemisTimeAgoPipe', () => {
    let pipe: ArtemisTimeAgoPipe;
    let translateService: TranslateService;
    let cdRef: ChangeDetectorRef;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ArtemisTimeAgoPipe],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                cdRef = { markForCheck: jest.fn() } as any as ChangeDetectorRef;

                translateService = TestBed.inject(TranslateService);
                translateService.use('en');
                const ngZone = TestBed.inject(NgZone);
                pipe = new ArtemisTimeAgoPipe(cdRef, ngZone, translateService);
            });
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
