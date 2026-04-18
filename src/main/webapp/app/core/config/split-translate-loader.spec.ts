import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { NavigationEnd, Router } from '@angular/router';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SplitTranslateLoader } from 'app/core/config/split-translate-loader';
import { Subject, firstValueFrom } from 'rxjs';

describe('SplitTranslateLoader', () => {
    setupTestBed({ zoneless: true });

    let loader: SplitTranslateLoader;
    let httpMock: HttpTestingController;
    let translateServiceMock: { setTranslation: ReturnType<typeof vi.fn> };
    let routerEvents: Subject<NavigationEnd>;
    const originalPathname = window.location.pathname;

    beforeEach(() => {
        vi.useFakeTimers();

        translateServiceMock = { setTranslation: vi.fn() };
        routerEvents = new Subject<NavigationEnd>();

        TestBed.configureTestingModule({
            providers: [
                SplitTranslateLoader,
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useValue: translateServiceMock },
                { provide: Router, useValue: { events: routerEvents.asObservable() } },
            ],
        });

        loader = TestBed.inject(SplitTranslateLoader);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        vi.useRealTimers();
        httpMock.verify();
        window.history.replaceState(null, '', originalPathname);
    });

    it('fetches the landing slice first on the landing route', async () => {
        window.history.replaceState(null, '', '/');

        const translationPromise = firstValueFrom(loader.getTranslation('en'));
        const req = httpMock.expectOne((r) => r.url.startsWith('i18n/en-landing.json'));
        req.flush({ landing: { hero: { title: 'Hello' } } });

        await expect(translationPromise).resolves.toEqual({ landing: { hero: { title: 'Hello' } } });
    });

    it('fetches the full bundle immediately on non-landing routes', async () => {
        window.history.replaceState(null, '', '/sign-in');

        const translationPromise = firstValueFrom(loader.getTranslation('en'));
        const req = httpMock.expectOne((r) => r.url.startsWith('i18n/en.json'));
        req.flush({ everything: true });

        await expect(translationPromise).resolves.toEqual({ everything: true });
    });

    it('schedules a background upgrade to the full bundle after landing hydration', async () => {
        window.history.replaceState(null, '', '/');

        const translationPromise = firstValueFrom(loader.getTranslation('en'));
        httpMock.expectOne((r) => r.url.startsWith('i18n/en-landing.json')).flush({ landing: {} });
        await translationPromise;

        // idle callback falls back to setTimeout (1500ms) in the jsdom test environment
        vi.advanceTimersByTime(1500);

        const fullReq = httpMock.expectOne((r) => r.url.startsWith('i18n/en.json'));
        fullReq.flush({ landing: {}, somethingElse: true });

        expect(translateServiceMock.setTranslation).toHaveBeenCalledWith('en', { landing: {}, somethingElse: true }, false);
    });

    it('triggers the upgrade immediately when the router navigates away from the landing route', async () => {
        window.history.replaceState(null, '', '/');

        const translationPromise = firstValueFrom(loader.getTranslation('en'));
        httpMock.expectOne((r) => r.url.startsWith('i18n/en-landing.json')).flush({});
        await translationPromise;

        // Before the idle timer fires, the router redirects the authenticated user to /courses
        routerEvents.next(new NavigationEnd(1, '/', '/courses'));

        const fullReq = httpMock.expectOne((r) => r.url.startsWith('i18n/en.json') && !r.url.includes('landing'));
        fullReq.flush({ landing: {}, course: {} });

        expect(translateServiceMock.setTranslation).toHaveBeenCalledWith('en', { landing: {}, course: {} }, false);

        // The idle timer must NOT trigger a second fetch after the router upgrade ran
        vi.advanceTimersByTime(2000);
        httpMock.expectNone((r) => r.url.startsWith('i18n/en.json') && !r.url.includes('landing'));
    });

    it('ignores same-route router events while on the landing page', async () => {
        window.history.replaceState(null, '', '/');

        const translationPromise = firstValueFrom(loader.getTranslation('en'));
        httpMock.expectOne((r) => r.url.startsWith('i18n/en-landing.json')).flush({});
        await translationPromise;

        // A hash/query-only navigation on `/` shouldn't start the full-bundle fetch
        routerEvents.next(new NavigationEnd(1, '/', '/'));
        httpMock.expectNone((r) => r.url.startsWith('i18n/en.json') && !r.url.includes('landing'));

        // Clean up the pending idle timer so `httpMock.verify()` doesn't flag it
        vi.advanceTimersByTime(2000);
        httpMock.expectOne((r) => r.url.startsWith('i18n/en.json') && !r.url.includes('landing')).flush({});
    });

    it('does not queue a second full-bundle fetch while one is in flight', async () => {
        window.history.replaceState(null, '', '/');

        const firstLanding = firstValueFrom(loader.getTranslation('en'));
        httpMock.expectOne((r) => r.url.startsWith('i18n/en-landing.json')).flush({});
        await firstLanding;

        vi.advanceTimersByTime(1500);
        // Upgrade has been initiated; leave the full-bundle response outstanding
        const pendingFull = httpMock.expectOne((r) => r.url.startsWith('i18n/en.json') && !r.url.includes('landing'));

        // A second call (e.g. explicit re-use) while the upgrade is pending must not queue another full fetch
        const secondLanding = firstValueFrom(loader.getTranslation('en'));
        httpMock.expectOne((r) => r.url.startsWith('i18n/en-landing.json')).flush({});
        await secondLanding;

        vi.advanceTimersByTime(1500);
        httpMock.expectNone((r) => r.url.startsWith('i18n/en.json') && !r.url.includes('landing'));

        pendingFull.flush({});
    });
});
