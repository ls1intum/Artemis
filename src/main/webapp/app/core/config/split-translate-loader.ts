import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injector, Provider, Service, inject } from '@angular/core';
import { TranslateLoader, TranslateService, TranslationObject } from '@ngx-translate/core';
import { NavigationEnd, Router } from '@angular/router';
import { Observable, filter, tap } from 'rxjs';
import { I18N_HASH } from 'app/core/environments/environment';

/* Idle timeout used while the user is still on the landing route — gives LCP and the
   below-the-fold deferred sections time to settle before the ~170 KB gzipped full bundle
   starts downloading. */
const LANDING_IDLE_TIMEOUT_MS = 2000;
const LANDING_FALLBACK_DELAY_MS = 1500;

const LANDING_PATH = '/';
const SIGN_IN_PATH = '/sign-in';
/* The routes that start from the slice. Navigating between them keeps the slice; any other route needs the full catalog. */
const SLICE_PATHS: ReadonlySet<string> = new Set([LANDING_PATH, SIGN_IN_PATH]);
/* What a visitor of the sign-in page does first, as opposed to what the page does itself: the username field is focused
   automatically, so `focusin` would start the download of the full catalog on every visit. */
const SIGN_IN_INTERACTION_EVENTS = ['pointerdown', 'keydown'] as const;

/** `'/sign-in/'` and `'/sign-in'` are the same route; an empty path is the root. */
function normalizePath(path: string): string {
    return path.replace(/\/+$/, '') || LANDING_PATH;
}

/**
 * Splits the initial translation fetch so the landing and the sign-in page only hydrate a small slice
 * (`landing`, `global`, `home` and `login`, among others) instead of the combined catalog. The full catalog is fetched
 * and merged when the router navigates away from the slice routes, so subsequent routes see the complete
 * catalog, and earlier than that:
 * - on the landing page when the browser is idle, as the page is only read;
 * - on the sign-in page with the first interaction of the visitor, so the catalog is on its way while they type
 *   and a visitor who only looks at the page does not download it.
 */
@Service()
export class SplitTranslateLoader implements TranslateLoader {
    private http = inject(HttpClient);
    private injector = inject(Injector);
    private destroyRef = inject(DestroyRef);

    private readonly fullyLoadedLangs = new Set<string>();
    /* Languages that have been queued for an upgrade but whose upgrade fetch hasn't started yet. */
    private readonly pendingUpgrades = new Set<string>();
    /* Languages whose upgrade fetch is in flight — used to prevent duplicate requests when
       both the idle timer and the router navigation trigger fire for the same language. */
    private readonly inFlightUpgrades = new Set<string>();
    private routerSubscribed = false;

    getTranslation(lang: string): Observable<TranslationObject> {
        if (this.fullyLoadedLangs.has(lang)) {
            return this.fetchFull(lang);
        }

        // Deep-linking any other route (e.g. /courses) needs the full catalog right away;
        // the slice intentionally doesn't cover course, exam, etc.
        if (!this.shouldServePartial()) {
            return this.fetchFull(lang);
        }

        this.ensureRouterUpgradeHook();
        return this.fetchPartial(lang).pipe(tap(() => this.scheduleUpgrade(lang)));
    }

    private fetchFull(lang: string): Observable<TranslationObject> {
        return this.http.get<TranslationObject>(`i18n/${lang}.json?_=${I18N_HASH}`).pipe(tap(() => this.fullyLoadedLangs.add(lang)));
    }

    private fetchPartial(lang: string): Observable<TranslationObject> {
        return this.http.get<TranslationObject>(`i18n/${lang}-landing.json?_=${I18N_HASH}`);
    }

    private shouldServePartial(): boolean {
        if (typeof window === 'undefined') {
            return false;
        }
        return SLICE_PATHS.has(normalizePath(window.location.pathname));
    }

    private scheduleUpgrade(lang: string): void {
        if (normalizePath(window.location.pathname) === SIGN_IN_PATH) {
            this.scheduleUpgradeOnInteraction(lang);
        } else {
            this.scheduleIdleUpgrade(lang);
        }
    }

    /* The first pointer or key press of the visitor starts the download. Until then, or until the router leaves the slice routes,
       the upgrade stays pending. */
    private scheduleUpgradeOnInteraction(lang: string): void {
        if (this.claimUpgrade(lang)) {
            this.armInteractionUpgrade(lang);
        }
    }

    /* Registers the listeners of the interaction trigger. They remove themselves when one of them fires, and when the loader is destroyed. */
    private armInteractionUpgrade(lang: string): void {
        const stopListening = new AbortController();
        this.destroyRef.onDestroy(() => stopListening.abort());
        const upgrade = () => {
            stopListening.abort();
            this.runUpgrade(lang);
        };
        for (const type of SIGN_IN_INTERACTION_EVENTS) {
            document.addEventListener(type, upgrade, { capture: true, passive: true, signal: stopListening.signal });
        }
    }

    private scheduleIdleUpgrade(lang: string): void {
        if (!this.claimUpgrade(lang)) {
            return;
        }

        const ric = (window as { requestIdleCallback?: (cb: () => void, opts?: { timeout: number }) => number }).requestIdleCallback;
        if (typeof ric === 'function') {
            ric(() => this.runUpgrade(lang), { timeout: LANDING_IDLE_TIMEOUT_MS });
            return;
        }
        setTimeout(() => this.runUpgrade(lang), LANDING_FALLBACK_DELAY_MS);
    }

    /* If the authenticated-user guard redirects from `/` to `/courses`, or the visitor leaves the sign-in page, we must upgrade
       translations immediately, because the slice doesn't cover course/exam/... keys. */
    private ensureRouterUpgradeHook(): void {
        if (this.routerSubscribed) {
            return;
        }
        const router = this.injector.get(Router, undefined, { optional: true });
        if (!router) {
            return;
        }
        this.routerSubscribed = true;
        router.events.pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd)).subscribe((event) => {
            // Strip BOTH query and hash fragments so anchor navigations on `/` (e.g. `/#features`)
            // stay on the landing route and don't prematurely trigger the full-bundle fetch.
            const target = normalizePath(event.urlAfterRedirects.split(/[?#]/, 1)[0]);
            if (SLICE_PATHS.has(target)) {
                return;
            }
            for (const lang of Array.from(this.pendingUpgrades)) {
                this.runUpgrade(lang);
            }
        });
    }

    private claimUpgrade(lang: string): boolean {
        if (this.fullyLoadedLangs.has(lang) || this.pendingUpgrades.has(lang) || this.inFlightUpgrades.has(lang)) {
            return false;
        }
        this.pendingUpgrades.add(lang);
        return true;
    }

    private runUpgrade(lang: string): void {
        if (!this.pendingUpgrades.has(lang)) {
            return;
        }
        // Move from pending → in-flight so concurrent triggers (idle + router) only fire once.
        this.pendingUpgrades.delete(lang);
        this.inFlightUpgrades.add(lang);

        this.fetchFull(lang).subscribe({
            next: (full) => {
                this.inFlightUpgrades.delete(lang);
                // Lazily resolve TranslateService to avoid a construction cycle (TranslateService
                // depends on this loader). By the time the upgrade runs, the service is ready.
                const translateService = this.injector.get(TranslateService, undefined, { optional: true });
                translateService?.setTranslation(lang, full, false);
            },
            error: () => {
                this.inFlightUpgrades.delete(lang);
                this.fullyLoadedLangs.delete(lang);
                // Re-queue so the next NavigationEnd (or the next getTranslation call for
                // this language) can actually retry the upgrade. On the sign-in page the next
                // interaction retries as well: the listeners of the failed attempt have fired.
                this.pendingUpgrades.add(lang);
                if (normalizePath(window.location.pathname) === SIGN_IN_PATH) {
                    this.armInteractionUpgrade(lang);
                }
            },
        });
    }
}

export const splitTranslateLoaderProviders: Provider[] = [{ provide: TranslateLoader, useExisting: SplitTranslateLoader }];
