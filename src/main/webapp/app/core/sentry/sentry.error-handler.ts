import { ErrorHandler, Injectable } from '@angular/core';
import { captureException, init } from '@sentry/browser';
import { VERSION } from 'app/app.constants';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ArtemisDeduplicate } from 'app/core/sentry/deduplicate.sentry-integration';

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
            integrations: [new ArtemisDeduplicate()],
        });
    }

    constructor() {
        super();
    }

    /**
     * Send an HttpError to Sentry. Only if it's not in the range 400-499.
     * @param error
     */
    handleError(error: any | undefined): void {
        if (error && error.name === 'HttpErrorResponse' && error.status < 500 && error.status >= 400) {
            super.handleError(error);
            return;
        }
        if (this.environment !== 'local') {
            captureException(error?.originalError || error);
        }
        super.handleError(error);
    }
}
