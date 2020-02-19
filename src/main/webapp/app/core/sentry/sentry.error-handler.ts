import { ErrorHandler, Injectable } from '@angular/core';
import { captureException, init } from '@sentry/browser';
import { VERSION } from 'app/app.constants';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';

@Injectable({ providedIn: 'root' })
export class SentryErrorHandler extends ErrorHandler {
    private static get environment(): string {
        switch (window.location.host) {
            case 'artemis.ase.in.tum.de':
                return 'prod';
            case 'artemistest.ase.in.tum.de':
                return 'test';
            case 'vmbruegge60.in.tum.de':
                return 'apitests';
            default:
                return 'local';
        }
    }

    public async initSentry(profileInfo: ProfileInfo): Promise<void> {
        if (!profileInfo || !profileInfo.sentry) {
            return;
        }

        init({
            dsn: profileInfo.sentry.dsn,
            release: VERSION,
            environment: SentryErrorHandler.environment,
        });
    }

    constructor() {
        super();
    }

    handleError(error: any): void {
        // We do not send to Sentry HttpError in the range 400-499
        if (error.name === 'HttpErrorResponse' && error.status < 500 && error.status >= 400) {
            super.handleError(error);
            return;
        }
        if (SentryErrorHandler.environment !== 'local') {
            captureException(error.originalError || error);
        }
        super.handleError(error);
    }
}
