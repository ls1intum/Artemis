import { ErrorHandler, Injectable, inject } from '@angular/core';
import { Event as SentryEvent, browserTracingIntegration, captureException, dedupeIntegration, init } from '@sentry/angular';
import { PROFILE_PROD, PROFILE_TEST, VERSION } from 'app/app.constants';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';

// `@sentry/angular` re-exports the public Sentry API but not the bare `Integration` type
// (that lives in `@sentry/core`, which we intentionally do not depend on directly).
// Derive it from an integration factory's return type instead of importing `@sentry/core`.
type Integration = ReturnType<typeof dedupeIntegration>;

@Injectable({ providedIn: 'root' })
export class SentryErrorHandler extends ErrorHandler {
    private environment: string;
    private localStorageService = inject(LocalStorageService);

    /**
     * Initialize Sentry with profile information.
     * @param profileInfo
     */
    public async initSentry(profileInfo: ProfileInfo): Promise<void> {
        if (!profileInfo || !profileInfo.sentry) {
            return;
        }

        // list of all integrations that should be active regardless of profile
        let integrations: Integration[] = [dedupeIntegration()];
        if (profileInfo.testServer != undefined) {
            if (profileInfo.testServer) {
                this.environment = PROFILE_TEST;
            } else {
                this.environment = PROFILE_PROD;
                // all Sentry integrations that should only be active in prod are added here
                integrations = integrations.concat([browserTracingIntegration()]);
            }
        } else {
            this.environment = 'local';
        }

        const defaultSampleRate: number = this.environment !== PROFILE_PROD ? 1.0 : 0.05;

        init({
            dsn: profileInfo.sentry.dsn,
            release: VERSION,
            environment: this.environment,
            integrations: integrations,
            sendDefaultPii: false,
            tracesSampler: (samplingContext) => {
                const { name, inheritOrSampleWith } = samplingContext;

                // Drop /api/public/time transactions entirely
                if (/^\/api\/public\/time(?:\?|$)/.test(name)) {
                    return 0.0;
                }
                // Sample less of the iris status transactions
                if (name.includes('api/iris/status')) {
                    return 0.001;
                }
                // Fall back to default sample rate
                return inheritOrSampleWith(defaultSampleRate);
            },
            beforeSend: (event) => {
                return this.scrubSentryPayload(event);
            },
            beforeSendTransaction: (t) => {
                return this.scrubSentryPayload(t);
            },
            beforeBreadcrumb: (crumb) => {
                if (crumb.message) {
                    crumb.message = this.scrubStringMessage(crumb.message);
                }
                return crumb;
            },
        });

        this.reportIfPasskeyIsNotSupported();
    }

    private scrubSentryPayload<T extends SentryEvent>(trans: T): T {
        if (trans.user) {
            delete trans.user;
        }

        if (trans.message) {
            trans.message = this.scrubStringMessage(trans.message);
        }

        if (trans.request) {
            if (trans.request.cookies) {
                delete trans.request.cookies;
            }

            if (trans.request.headers) {
                trans.request.headers = Object.fromEntries(Object.entries(trans.request.headers).filter(([key]) => !key.toLowerCase().startsWith('x-artemis-client-')));
            }
        }

        if (trans.exception && trans.exception.values) {
            for (const ex of trans.exception.values) {
                if (ex.value) {
                    ex.value = this.scrubStringMessage(ex.value);
                }
            }
        }

        if (trans.request && trans.request.url) {
            trans.request.url = this.scrubUrl(trans.request.url);
        }

        return trans;
    }

    private scrubStringMessage(message: string): string {
        const piiPatterns = [/user=\S+/g, /User\{[^}]*\}/g, /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g];
        for (const pattern of piiPatterns) {
            message = message.replace(pattern, '');
        }
        return message;
    }

    private scrubUrl(url: string): string {
        const scrubbed: string = url.replace(/\/git\/([A-Z0-9]+)\/([^/]+)-[^/]+\.git/g, '/git/$1/$2.git');

        if (url.includes('-tests.git') || url.includes('-exercise.git') || url.includes('-solution.git')) {
            return url;
        }
        return scrubbed;
    }

    /**
     * Send an HttpError to Sentry. Only if it's not in the range 400-499.
     * @param error
     */
    override handleError(error: unknown): void {
        // Narrow to the loosely-typed error shape without changing runtime behavior:
        // errors reaching Angular's ErrorHandler may be reconstructed HttpErrorResponses, so we keep the
        // existing string-based `name` check (rather than `instanceof`) and read fields defensively.
        const err = error as { name?: string; status?: number; error?: unknown; message?: unknown; originalError?: unknown };
        if (err && err.name === 'HttpErrorResponse' && err.status! < 500 && err.status! >= 400) {
            super.handleError(error);
            return;
        }
        if (this.environment !== 'local') {
            // Non-optional access is deliberate: it preserves the original behavior where a null/undefined
            // error throws here (a pre-existing quirk asserted by the spec) rather than capturing `null`.
            const exception = err.error || err.message || err.originalError || error;
            captureException(exception);
        }
        super.handleError(error);
    }

    private isSameDay(firstDay: Date, secondDay: Date): boolean {
        return firstDay.getFullYear() === secondDay.getFullYear() && firstDay.getMonth() === secondDay.getMonth() && firstDay.getDate() === secondDay.getDate();
    }

    private hasBeenReportedToday() {
        const lastReported = this.localStorageService.retrieveDate('webauthnNotSupportedTimestamp');
        const today = new Date();
        return lastReported && this.isSameDay(lastReported, today);
    }

    /**
     * Reports to Sentry if the browser does not support WebAuthn (required for Passkey authentication).
     *
     * The message is only reported once per day per browser of a user.
     */
    private reportIfPasskeyIsNotSupported() {
        const isWebAuthnUsable = window.PublicKeyCredential;
        if (!isWebAuthnUsable) {
            if (this.hasBeenReportedToday()) {
                return;
            }

            this.localStorageService.store<Date>('webauthnNotSupportedTimestamp', new Date());
            captureException(new Error('Browser does not support WebAuthn - no Passkey authentication possible'), {
                tags: {
                    feature: 'Passkey Authentication',
                    browser: navigator.userAgent,
                },
            });
        }
    }
}
