import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ChangeDetectorRef } from '@angular/core';
import dayjs from 'dayjs/esm';
// Register the locales used in this spec. Under Jest these were loaded transitively via the global
// setup importing 'app/core/config/dayjs'; the Vitest setup does not, so import them explicitly here.
import 'dayjs/esm/locale/en';
import 'dayjs/esm/locale/de';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClient } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ArtemisTimeAgoPipe', () => {
    setupTestBed({ zoneless: true });

    let pipe: ArtemisTimeAgoPipe;
    let translateService: TranslateService;
    const cdRef = { markForCheck: vi.fn() } as any as ChangeDetectorRef;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [ArtemisTimeAgoPipe, { provide: ChangeDetectorRef, useValue: cdRef }, { provide: TranslateService, useClass: MockTranslateService }, provideHttpClient()],
        }).compileComponents();
        translateService = TestBed.inject(TranslateService);
        translateService.use('en');
        pipe = TestBed.inject(ArtemisTimeAgoPipe);
    });

    afterEach(() => {
        vi.restoreAllMocks();
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

    it('updates the output automatically as time passes, but removes the timer after destroy', async () => {
        vi.useFakeTimers();
        try {
            const time = dayjs().subtract(60, 'seconds');
            expect(pipe.transform(time)).toBe('a minute ago');
            expect(cdRef.markForCheck).not.toHaveBeenCalled();
            await vi.advanceTimersByTimeAsync(30000);
            expect(cdRef.markForCheck).toHaveBeenCalledOnce();
            expect(pipe.transform(time)).toBe('2 minutes ago');

            pipe.ngOnDestroy();
            await vi.advanceTimersByTimeAsync(30000);
            expect(cdRef.markForCheck).toHaveBeenCalledOnce(); // not a second time
        } finally {
            vi.useRealTimers();
        }
    });
});
