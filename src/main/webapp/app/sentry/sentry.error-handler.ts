import { Injectable, ErrorHandler } from '@angular/core';
import { init, captureException, showReportDialog, ReportDialogOptions } from '@sentry/browser';
import { AccountService, User } from 'app/core';
import { VERSION } from 'app/app.constants';

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

    private static initSentry(): void {
        init({
            dsn: 'https://8c6b41ec2d4245e8bd3ec9541d53f625@sentry.io/1440029',
            release: VERSION,
            environment: SentryErrorHandler.environment,
        });
    }

    constructor(private accountService: AccountService) {
        SentryErrorHandler.initSentry();

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
