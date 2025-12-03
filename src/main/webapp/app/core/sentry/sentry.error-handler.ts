import { ErrorHandler, Injectable, inject } from '@angular/core';
import {
    browserTracingIntegration,
    captureException,
    dedupeIntegration,
    init,
} from "@sentry/angular";
import type { Integration } from '@sentry/core';
import { PROFILE_PROD, PROFILE_TEST, VERSION } from 'app/app.constants';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

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
                integrations = integrations.concat([
                    browserTracingIntegration(),
                ]);
            }
        } else {
            this.environment = 'local';
        }

        let defaultSampleRate: number =
            this.environment !== PROFILE_PROD ? 1.0 : 0.05;

        init({
            dsn: profileInfo.sentry.dsn,
            release: VERSION,
            environment: this.environment,
            integrations: integrations,
            tracesSampler: (samplingContext) => {
                const { name, inheritOrSampleWith } = samplingContext;

                // Sample none of the time transactions
                if (name.includes("api/core/public/time")) {
                    return 0.0;
                }
                // Sample less of the iris status transactions
                if (name.includes("api/iris/status")) {
                    return 0.001;
                }
                // Fall back to default sample rate
                return inheritOrSampleWith(defaultSampleRate);
            },
        });

        this.reportIfPasskeyIsNotSupported();
    }

    /**
     * Send an HttpError to Sentry. Only if it's not in the range 400-499.
     * @param error
     */
    override handleError(error: any): void {
        if (error && error.name === 'HttpErrorResponse' && error.status < 500 && error.status >= 400) {
            super.handleError(error);
            return;
        }
        if (this.environment !== 'local') {
            const exception = error.error || error.message || error.originalError || error;
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
