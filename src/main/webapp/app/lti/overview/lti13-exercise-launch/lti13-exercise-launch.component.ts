import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { captureException } from 'app/core/error-reporting/capture-exception';
import { LtiService } from 'app/foundation/service/lti.service';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { LtiLoginContinuationService } from './lti-login-continuation.service';
import { LtiApi } from 'app/openapi/api/lti-api';
import { Lti13AuthenticationResponse } from 'app/openapi/model/lti13authentication-response';

@Component({
    selector: 'jhi-lti-exercise-launch',
    templateUrl: './lti13-exercise-launch.component.html',
    imports: [TranslateDirective],
})
export class Lti13ExerciseLaunchComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private ltiApi = inject(LtiApi);
    private accountService = inject(AccountService);
    private router = inject(Router);
    private sessionStorageService = inject(SessionStorageService);
    private ltiService = inject(LtiService);
    private themeService = inject(ThemeService);
    private loginContinuation = inject(LtiLoginContinuationService);

    readonly isLaunching = signal(true);

    /**
     * perform an LTI launch with state and id_token query parameters
     */
    ngOnInit(): void {
        this.sendRequest();
    }

    sendRequest(): void {
        const state = this.route.snapshot.queryParamMap.get('state');
        const idToken = this.route.snapshot.queryParamMap.get('id_token');

        if (!state || !idToken) {
            captureException('Required parameter for LTI launch missing');
            this.isLaunching.set(false);
            return;
        }

        this.loginContinuation.clearPendingLaunch();

        this.ltiApi.lti13AuthLogin(state, idToken).subscribe({
            next: (data) => {
                this.handleLtiLaunchSuccess(data);
            },
            error: (error) => {
                if (error.status === 401) {
                    this.authenticateUserThenRedirect(error);
                } else {
                    this.handleLtiLaunchError();
                }
            },
        });
    }

    authenticateUserThenRedirect(error: HttpErrorResponse): void {
        const loginName = error.headers.get('ltiSuccessLoginRequired');
        void this.accountService.identity().then((user) => {
            if (user) {
                this.redirectUserToTargetLink(error);
            } else {
                if (loginName) {
                    this.accountService.setPrefilledUsername(loginName);
                }
                this.redirectUserToLoginThenTargetLink(error);
            }
        });
    }

    redirectUserToTargetLink(data: HttpErrorResponse): void {
        // A 401 carries the same body as a successful launch: the user has to sign in before it continues.
        const errorBody = data.error as Lti13AuthenticationResponse;
        const ltiIdToken = errorBody['ltiIdToken'];
        const clientRegistrationId = errorBody['clientRegistrationId'];

        this.storeLtiSessionData(ltiIdToken, clientRegistrationId);

        const targetLinkUri = errorBody['targetLinkUri'];
        if (!targetLinkUri) {
            captureException('No LTI targetLinkUri received for a launch that requires sign-in');
            this.handleLtiLaunchError();
            return;
        }
        // Redirect to target link since the user is already logged in
        this.replaceWindowLocationWrapper(targetLinkUri);
    }

    redirectUserToLoginThenTargetLink(error: HttpErrorResponse): void {
        const body = error.error as Lti13AuthenticationResponse;
        // prepare rejects and reports a missing value.
        if (!this.loginContinuation.prepare(body.targetLinkUri ?? '', body.ltiIdToken ?? '', body.clientRegistrationId ?? '')) {
            this.handleLtiLaunchError();
            return;
        }
        // Home owns the continuation after authentication, including a full-page identity-provider round trip.
        void this.router.navigate(['/sign-in']);
    }

    handleLtiLaunchSuccess(data: Lti13AuthenticationResponse): void {
        const targetLinkUri = data.targetLinkUri;
        const ltiIdToken = data.ltiIdToken;
        const clientRegistrationId = data.clientRegistrationId;

        this.sessionStorageService.remove('state');
        this.storeLtiSessionData(ltiIdToken, clientRegistrationId);

        if (targetLinkUri) {
            this.replaceWindowLocationWrapper(targetLinkUri);
        } else {
            this.isLaunching.set(false);
            captureException('No LTI targetLinkUri received for a successful launch');
        }
    }

    handleLtiLaunchError(): void {
        this.sessionStorageService.remove('state');
        this.isLaunching.set(false);
    }

    storeLtiSessionData(ltiIdToken: string | undefined, clientRegistrationId: string | undefined): void {
        if (!ltiIdToken) {
            captureException(new Error('LTI ID token required to store session data.'));
            return;
        }

        if (!clientRegistrationId) {
            captureException(new Error('LTI client registration ID required to store session data.'));
            return;
        }

        try {
            this.sessionStorageService.store<string>('ltiIdToken', ltiIdToken);
            this.sessionStorageService.store<string>('clientRegistrationId', clientRegistrationId);
        } catch (error) {
            captureException('Failed to store session data: ' + String(error));
        }
    }

    replaceWindowLocationWrapper(url: string): void {
        this.ltiService.setShownViaLti(true);
        this.themeService.applyThemePreference(Theme.LIGHT);

        let path;
        const queryParams: { [key: string]: string } = {};

        if (url === '/lti/select-course') {
            path = url;
        } else {
            const urlObj = new URL(url);
            if (urlObj.searchParams.get('isMultiLaunch') === 'true') {
                this.ltiService.setMultiLaunch(true);
            }

            path = urlObj.pathname;
            urlObj.searchParams.forEach((value, key) => {
                queryParams[key] = value;
            });
        }

        void this.router.navigate([path], { queryParams: queryParams, replaceUrl: true });
    }
}
