import { Injectable, ErrorHandler } from '@angular/core';
import { init, captureException, showReportDialog, ReportDialogOptions } from '@sentry/browser';
import { AccountService, User } from 'app/core';
import { VERSION } from 'app/app.constants';
import { ProfileService } from 'app/layouts';

@Injectable()
export class SentryErrorHandler implements ErrorHandler {
    currAccount: User;

    private static get environment(): string {
        switch (window.location.host) {
            case 'artemis.ase.in.tum.de':
                return 'prod';
            case 'artemistest.ase.in.tum.de':
                return 'test';
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

    constructor(private accountService: AccountService, private profileService: ProfileService) {
        // noinspection JSIgnoredPromiseFromCall
        this.initSentry();

        // noinspection JSIgnoredPromiseFromCall
        this.getCurrentAccount();
    }

    private async getCurrentAccount(): Promise<void> {
        if (!this.currAccount && this.accountService.isAuthenticated()) {
            this.currAccount = await this.accountService.identity();
        }
    }

    handleError(error: any): void {
        const eventId = captureException(error.originalError || error);

        const dialogOptions: ReportDialogOptions = { eventId };
        if (this.currAccount) {
            dialogOptions.user = {
                email: this.currAccount.email,
                name: this.currAccount.login,
            };
        }
        showReportDialog(dialogOptions);
    }
}
