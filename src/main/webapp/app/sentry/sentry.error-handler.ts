import { Injectable, ErrorHandler } from '@angular/core';
import { init, captureException } from '@sentry/browser';
import { VERSION } from 'app/app.constants';
import { ProfileService } from 'app/layouts';

@Injectable()
export class SentryErrorHandler implements ErrorHandler {
    private static get environment(): string {
        switch (window.location.host) {
            case 'artemis.ase.in.tum.de':
                return 'prod';
            case 'artemistest.ase.in.tum.de':
                return 'test';
            case 'vmbruegge60.in.tum.de':
                return 'e2e';
            default:
                return 'local';
        }
    }

    private async initSentry(): Promise<void> {
        const profileInfo = await this.profileService.getProfileInfo();

        if (!profileInfo.sentry) {
            return;
        }

        init({
            dsn: profileInfo.sentry.dsn,
            release: VERSION,
            environment: SentryErrorHandler.environment,
        });
    }

    constructor(private profileService: ProfileService) {
        // noinspection JSIgnoredPromiseFromCall
        this.initSentry();
    }

    handleError(error: any): void {
        // We do not send to Sentry HttpError in the range 400-499
        if (error.name === 'HttpErrorResponse' && error.status < 500 && error.status >= 400) {
            throw error;
        }

        captureException(error.originalError || error);
        throw error;
    }
}
