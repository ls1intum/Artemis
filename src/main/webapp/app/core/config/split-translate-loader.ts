import { HttpClient } from '@angular/common/http';
import { Injectable, Injector, Provider, inject } from '@angular/core';
import { TranslateLoader, TranslateService, TranslationObject } from '@ngx-translate/core';
import { NavigationEnd, Router } from '@angular/router';
import { Observable, filter, tap } from 'rxjs';
import { I18N_HASH } from 'app/core/environments/environment';

/* Idle timeout used while the user is still on the landing route — gives LCP and the
   below-the-fold deferred sections time to settle before the ~170 KB gzipped full bundle
   starts downloading. */
const LANDING_IDLE_TIMEOUT_MS = 2000;
const LANDING_FALLBACK_DELAY_MS = 1500;

/**
 * Splits the initial translation fetch so the landing page only hydrates a small slice
 * (`landing` + `global`) instead of the 600 KB combined bundle. Once the browser is idle —
 * or the router navigates away from `/` — the full bundle is fetched and merged, so
 * subsequent routes see the complete catalog.
 */
@Injectable({ providedIn: 'root' })
export class SplitTranslateLoader implements TranslateLoader {
    private http = inject(HttpClient);
    private injector = inject(Injector);

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

        // Deep-linking a non-landing route (e.g. /sign-in) needs the full catalog right away;
        // the partial bundle intentionally doesn't cover sign-in, course, exam, etc.
        if (!this.shouldServePartial()) {
            return this.fetchFull(lang);
        }

        this.ensureRouterUpgradeHook();
        return this.fetchPartial(lang).pipe(tap(() => this.scheduleIdleUpgrade(lang)));
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
        const path = window.location.pathname;
        return path === '/' || path === '';
    }

    private scheduleIdleUpgrade(lang: string): void {
        if (!this.claimUpgrade(lang)) {
            return;
        }

        const ric = (globalThis as { requestIdleCallback?: (cb: () => void, opts?: { timeout: number }) => number }).requestIdleCallback;
        if (typeof ric === 'function') {
            ric(() => this.runUpgrade(lang), { timeout: LANDING_IDLE_TIMEOUT_MS });
            return;
        }
        setTimeout(() => this.runUpgrade(lang), LANDING_FALLBACK_DELAY_MS);
    }

    /* If the authenticated-user guard redirects from `/` to `/courses`, we must upgrade
       translations immediately — the landing slice doesn't cover course/exam/... keys. */
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
            const target = event.urlAfterRedirects.split(/[?#]/, 1)[0];
            if (target === '/' || target === '') {
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
                // this language) can actually retry the upgrade.
                this.pendingUpgrades.add(lang);
            },
        });
    }
}

export const splitTranslateLoaderProviders: Provider[] = [{ provide: TranslateLoader, useExisting: SplitTranslateLoader }];
