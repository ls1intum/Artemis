import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { NavigationEnd, Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SplitTranslateLoader } from 'app/core/config/split-translate-loader';
import { Subject, firstValueFrom } from 'rxjs';

describe('SplitTranslateLoader', () => {
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

    it('fetches the full bundle immediately on routes that are not served from the slice', async () => {
        window.history.replaceState(null, '', '/courses');

        const translationPromise = firstValueFrom(loader.getTranslation('en'));
        const req = httpMock.expectOne((r) => r.url.startsWith('i18n/en.json'));
        req.flush({ everything: true });

        await expect(translationPromise).resolves.toEqual({ everything: true });
    });

    describe('on the sign-in route', () => {
        const isFullBundle = (r: { url: string }) => r.url.startsWith('i18n/en.json');

        async function hydrateSignInSlice(path = '/sign-in'): Promise<void> {
            window.history.replaceState(null, '', path);
            const translationPromise = firstValueFrom(loader.getTranslation('en'));
            httpMock.expectOne((r) => r.url.startsWith('i18n/en-landing.json')).flush({ home: {} });
            await translationPromise;
        }

        it('fetches the slice instead of the full bundle', async () => {
            await hydrateSignInSlice();

            httpMock.expectNone(isFullBundle);
        });

        it('serves the slice for the sign-in route with a trailing slash', async () => {
            await hydrateSignInSlice('/sign-in/');

            httpMock.expectNone(isFullBundle);
        });

        it('does not fetch the full bundle while the visitor only looks at the page', async () => {
            await hydrateSignInSlice();

            // Neither the idle timer of the landing page nor any other timer starts the download
            vi.advanceTimersByTime(60_000);

            httpMock.expectNone(isFullBundle);
        });

        it.each(['pointerdown', 'keydown'])('fetches the full bundle with the first %s of the visitor', async (eventType) => {
            await hydrateSignInSlice();

            document.dispatchEvent(new Event(eventType));

            httpMock.expectOne(isFullBundle).flush({ home: {}, artemisApp: {} });
            expect(translateServiceMock.setTranslation).toHaveBeenCalledWith('en', { home: {}, artemisApp: {} }, false);
        });

        it('fetches the full bundle once, however many interactions follow', async () => {
            await hydrateSignInSlice();

            document.dispatchEvent(new Event('pointerdown'));
            document.dispatchEvent(new Event('keydown'));
            document.dispatchEvent(new Event('pointerdown'));

            httpMock.expectOne(isFullBundle).flush({});
            httpMock.expectNone(isFullBundle);
        });

        it('does not treat the automatic focus of the username field as an interaction', async () => {
            await hydrateSignInSlice();

            document.dispatchEvent(new Event('focusin'));

            httpMock.expectNone(isFullBundle);
        });

        it('keeps the slice when the router stays on the sign-in route', async () => {
            await hydrateSignInSlice();

            routerEvents.next(new NavigationEnd(1, '/sign-in', '/sign-in?redirectUrl=%2Fcourses'));

            httpMock.expectNone(isFullBundle);
        });

        it('fetches the full bundle when the router leaves the sign-in route', async () => {
            await hydrateSignInSlice();

            routerEvents.next(new NavigationEnd(1, '/sign-in', '/courses'));

            httpMock.expectOne(isFullBundle).flush({});
        });

        it('does not fetch the full bundle again for an interaction after the router upgrade ran', async () => {
            await hydrateSignInSlice();
            routerEvents.next(new NavigationEnd(1, '/sign-in', '/courses'));
            httpMock.expectOne(isFullBundle).flush({});

            document.dispatchEvent(new Event('pointerdown'));

            httpMock.expectNone(isFullBundle);
        });
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

    it('treats hash-fragment navigations on the landing route as same-route', async () => {
        window.history.replaceState(null, '', '/');

        const translationPromise = firstValueFrom(loader.getTranslation('en'));
        httpMock.expectOne((r) => r.url.startsWith('i18n/en-landing.json')).flush({});
        await translationPromise;

        // A hash-only navigation like /#features (e.g. anchor links in the spotlight) must NOT
        // trigger the full bundle fetch — we'd defeat the landing split otherwise.
        routerEvents.next(new NavigationEnd(1, '/#features', '/#features'));
        httpMock.expectNone((r) => r.url.startsWith('i18n/en.json') && !r.url.includes('landing'));

        // The idle timer still fires eventually and flushes the full bundle
        vi.advanceTimersByTime(2000);
        httpMock.expectOne((r) => r.url.startsWith('i18n/en.json') && !r.url.includes('landing')).flush({});
    });

    it('re-queues the upgrade after a failed full-bundle fetch so a retry can run', async () => {
        window.history.replaceState(null, '', '/');

        const translationPromise = firstValueFrom(loader.getTranslation('en'));
        httpMock.expectOne((r) => r.url.startsWith('i18n/en-landing.json')).flush({});
        await translationPromise;

        vi.advanceTimersByTime(2000);
        const failingReq = httpMock.expectOne((r) => r.url.startsWith('i18n/en.json') && !r.url.includes('landing'));
        failingReq.error(new ProgressEvent('Network error'));

        // A subsequent NavigationEnd to a non-landing route must kick off a retry
        routerEvents.next(new NavigationEnd(1, '/', '/courses'));
        httpMock.expectOne((r) => r.url.startsWith('i18n/en.json') && !r.url.includes('landing')).flush({ ok: true });
        expect(translateServiceMock.setTranslation).toHaveBeenCalledWith('en', { ok: true }, false);
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
