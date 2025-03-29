import { ErrorHandler, Injectable } from '@angular/core';
import { captureException, dedupeIntegration, init } from '@sentry/angular';
import { VERSION } from 'app/app.constants';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

@Injectable({ providedIn: 'root' })
export class SentryErrorHandler extends ErrorHandler {
    private environment: string;

    /**
     * Initialize Sentry with profile information.
     * @param profileInfo
     */
    public async initSentry(profileInfo: ProfileInfo): Promise<void> {
        if (!profileInfo || !profileInfo.sentry) {
            return;
        }

        if (profileInfo.testServer != undefined) {
            if (profileInfo.testServer) {
                this.environment = 'test';
            } else {
                this.environment = 'prod';
            }
        } else {
            this.environment = 'local';
        }

        init({
            dsn: profileInfo.sentry.dsn,
            release: VERSION,
            environment: this.environment,
            integrations: [dedupeIntegration()],
            tracesSampleRate: this.environment !== 'prod' ? 1.0 : 0.2,
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

    /**
     * Extracts the date part from an ISO 8601 formatted string.
     *
     * @param isoString The ISO 8601 formatted string (e.g., "YYYY-MM-DDTHH:mm:ss.sssZ").
     * @return The date part of the ISO string (e.g., "YYYY-MM-DD").
     */
    private getDatePartFromISOString(isoString: string | null): string {
        return isoString ? isoString.split('T')[0] : '';
    }

    private hasBeenReportedToday() {
        const lastReported = localStorage.getItem('webauthnNotSupportedTimestamp');
        const dateToday = this.getDatePartFromISOString(new Date().toISOString());
        const dateLastReported = this.getDatePartFromISOString(lastReported);
        return lastReported && dateLastReported === dateToday;
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

            localStorage.setItem('webauthnNotSupportedTimestamp', new Date().toISOString());
            captureException(new Error('Browser does not support WebAuthn - no Passkey authentication possible'), {
                tags: {
                    feature: 'Passkey Authentication',
                    browser: navigator.userAgent,
                },
            });
        }
    }
}
