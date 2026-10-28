import { Injector, Service, inject } from '@angular/core';
import { Router } from '@angular/router';
import { captureException } from '@sentry/angular';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { LtiService } from 'app/foundation/service/lti.service';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';

interface PendingLtiLaunch {
    url: string;
    ltiIdToken: string;
    clientRegistrationId: string;
}

const PENDING_LTI_LAUNCH = 'ltiPendingLaunch';

/** Keeps an interactive LTI launch across password login and external identity-provider round trips. */
@Service()
export class LtiLoginContinuationService {
    private readonly storage = inject(SessionStorageService);
    private readonly router = inject(Router);
    private readonly ltiService = inject(LtiService);
    private readonly injector = inject(Injector);

    private navigation?: Promise<void>;

    /** Save launch context before leaving the page for interactive authentication. */
    prepare(targetLinkUri: string, ltiIdToken: string, clientRegistrationId: string): boolean {
        const url = this.localTarget(targetLinkUri);
        if (!url || !ltiIdToken || !clientRegistrationId) {
            captureException(new Error('Invalid interactive LTI launch continuation'));
            return false;
        }
        this.storage.store<PendingLtiLaunch>(PENDING_LTI_LAUNCH, { url, ltiIdToken, clientRegistrationId });
        return true;
    }

    hasPendingLaunch(): boolean {
        return !!this.storage.retrieve<PendingLtiLaunch>(PENDING_LTI_LAUNCH);
    }

    /** A new launch supersedes an abandoned login continuation, including launches that authenticate directly. */
    clearPendingLaunch(): void {
        this.storage.remove(PENDING_LTI_LAUNCH);
    }

    /** Rejected credentials must clear authentication caches while retaining the launch for another login attempt. */
    preserveForLoginRetry(clearAuthentication: () => void): void {
        const pending = this.storage.retrieve<PendingLtiLaunch>(PENDING_LTI_LAUNCH);
        clearAuthentication();
        if (pending) {
            this.prepare(pending.url, pending.ltiIdToken, pending.clientRegistrationId);
        }
    }

    /**
     * Owns the post-login redirect when a launch is pending. Cancelled navigation leaves it available to retry;
     * successful navigation consumes it once, ahead of the ordinary previous-URL or course-list redirect.
     */
    resume(): boolean {
        const pending = this.storage.retrieve<PendingLtiLaunch>(PENDING_LTI_LAUNCH);
        if (!pending) {
            return false;
        }
        if (this.navigation) {
            return true;
        }
        const url = this.localTarget(pending.url);
        if (!url || typeof pending.ltiIdToken !== 'string' || !pending.ltiIdToken || typeof pending.clientRegistrationId !== 'string' || !pending.clientRegistrationId) {
            this.storage.remove(PENDING_LTI_LAUNCH);
            captureException(new Error('Invalid stored LTI launch continuation'));
            return false;
        }

        this.storage.store('ltiIdToken', pending.ltiIdToken);
        this.storage.store('clientRegistrationId', pending.clientRegistrationId);
        this.ltiService.setShownViaLti(true);
        this.ltiService.setMultiLaunch(new URL(url, window.location.origin).searchParams.get('isMultiLaunch') === 'true');
        this.injector.get(ThemeService).applyThemePreference(Theme.LIGHT);
        this.navigation = this.router
            .navigateByUrl(url, { replaceUrl: true })
            .then((navigated) => {
                const current = this.storage.retrieve<PendingLtiLaunch>(PENDING_LTI_LAUNCH);
                if (navigated && current?.ltiIdToken === pending.ltiIdToken && current?.url === pending.url && current?.clientRegistrationId === pending.clientRegistrationId) {
                    this.storage.remove(PENDING_LTI_LAUNCH);
                    this.storage.remove('previousUrl');
                    this.storage.remove('state');
                }
            })
            .catch((error: unknown) => {
                captureException(error);
            })
            .finally(() => {
                this.navigation = undefined;
            });
        return true;
    }

    private localTarget(target: string): string | undefined {
        if (typeof target !== 'string' || !target) {
            return undefined;
        }
        try {
            const url = new URL(target, window.location.origin);
            if (url.origin !== window.location.origin || url.username || url.password || !url.pathname.startsWith('/') || url.pathname.startsWith('//')) {
                return undefined;
            }
            return url.pathname + url.search + url.hash;
        } catch {
            return undefined;
        }
    }
}
