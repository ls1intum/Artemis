import { Injectable, ErrorHandler } from '@angular/core';
import { init, captureException } from '@sentry/browser';
import { VERSION } from 'app/app.constants';
import { ProfileInfo, ProfileService } from 'app/layouts';

@Injectable()
export class SentryErrorHandler extends ErrorHandler {
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
        this.profileService.getProfileInfo().subscribe((profileInfo: ProfileInfo) => {
            if (!profileInfo || !profileInfo.sentry) {
                return;
            }

            init({
                dsn: profileInfo.sentry.dsn,
                release: VERSION,
                environment: SentryErrorHandler.environment,
            });
        });
    }

    constructor(private profileService: ProfileService) {
        super();
        // noinspection JSIgnoredPromiseFromCall
        this.initSentry();
    }

    handleError(error: any): void {
        // We do not send to Sentry HttpError in the range 400-499
        if (error.name === 'HttpErrorResponse' && error.status < 500 && error.status >= 400) {
            super.handleError(error);
            return;
        }

        captureException(error.originalError || error);
        super.handleError(error);
    }
}
