import { Component, OnInit, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { FeatureOverlayComponent } from 'app/shared/components/feature-overlay/feature-overlay.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import {
    faBell,
    faBookOpen,
    faBroom,
    faEye,
    faFlag,
    faGears,
    faHeart,
    faList,
    faLock,
    faPuzzlePiece,
    faRobot,
    faStamp,
    faTachometerAlt,
    faTasks,
    faThLarge,
    faToggleOn,
    faUniversity,
    faUser,
    faUserPlus,
} from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { IsLoggedInWithPasskeyGuard } from 'app/core/auth/is-logged-in-with-passkey/is-logged-in-with-passkey.guard';
import { InvalidCredentialError } from 'app/core/user/settings/passkey-settings/entities/invalid-credential-error';
import { getCredentialWithGracefullyHandlingAuthenticatorIssues } from 'app/core/user/settings/passkey-settings/util/credential.util';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { AlertService } from 'app/shared/service/alert.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { AccountService } from 'app/core/auth/account.service';
import { LoginWithPasskeyModal } from 'app/core/navbar/server-administration/login-with-passkey/login-with-passkey.modal';

@Component({
    selector: 'jhi-server-administration',
    imports: [
        FeatureOverlayComponent,
        TranslateDirective,
        FaIconComponent,
        HasAnyAuthorityDirective,
        NgbDropdown,
        NgbDropdownMenu,
        NgbDropdownToggle,
        RouterLinkActive,
        RouterLink,
        LoginWithPasskeyModal,
    ],
    templateUrl: './server-administration.html',
    styleUrl: '../navbar.scss',
})
export class ServerAdministration implements OnInit {
    protected readonly faUniversity = faUniversity;
    protected readonly faStamp = faStamp;
    protected readonly faTasks = faTasks;
    protected readonly faHeart = faHeart;
    protected readonly faTachometerAlt = faTachometerAlt;
    protected readonly faToggleOn = faToggleOn;
    protected readonly faBookOpen = faBookOpen;
    protected readonly faGears = faGears;
    protected readonly faBroom = faBroom;
    protected readonly faThLarge = faThLarge;
    protected readonly faFlag = faFlag;
    protected readonly faPuzzlePiece = faPuzzlePiece;
    protected readonly faRobot = faRobot;
    protected readonly faList = faList;
    protected readonly faBell = faBell;
    protected readonly faLock = faLock;
    protected readonly faEye = faEye;
    protected readonly faUser = faUser;
    protected readonly faUserPlus = faUserPlus;

    private readonly isLoggedInWithPasskeyGuard = inject(IsLoggedInWithPasskeyGuard);
    private readonly webauthnService = inject(WebauthnService);
    private readonly webauthnApiService = inject(WebauthnApiService);
    private readonly alertService = inject(AlertService);
    private readonly router = inject(Router);
    private readonly eventManager = inject(EventManager);
    private readonly accountService = inject(AccountService);

    adminMenuDropdown = viewChild.required<NgbDropdown>('adminMenuDropdown');
    loginWithPasskeyDialog = viewChild.required<LoginWithPasskeyModal>(LoginWithPasskeyModal);

    isExamActive = input<boolean>(false);
    isExamStarted = input<boolean>(false);
    localCIActive = input<boolean>(false);
    irisEnabled = input<boolean>(false);
    ltiEnabled = input<boolean>(false);
    standardizedCompetenciesEnabled = input<boolean>(false);
    atlasEnabled = input<boolean>(false);
    examEnabled = input<boolean>(false);

    collapseNavbarListener = output<void>();

    authenticationError = false;

    protected isLoggedInWithPasskey = signal<boolean>(false);
    private justLoggedInWithPasskey = false;

    constructor() {
        effect(() => {
            this.openDropdownIfUserLoggedInWithPasskey();
        });
    }

    private openDropdownIfUserLoggedInWithPasskey() {
        if (this.isLoggedInWithPasskey() && this.justLoggedInWithPasskey) {
            this.justLoggedInWithPasskey = false;

            // Use setTimeout to wait for the next JavaScript tick (macrotask).
            // This allows Angular to finish re-rendering the dropdown's
            // new content *before* we try to open and position it.
            setTimeout(() => {
                this.adminMenuDropdown().open();
            }, 0);
        }
    }

    ngOnInit() {
        this.isLoggedInWithPasskey.set(this.isLoggedInWithPasskeyGuard.isLoggedInWithPasskey());
    }

    protected collapseNavbar() {
        this.collapseNavbarListener.emit();
    }

    protected showDialog() {
        this.loginWithPasskeyDialog().showDialog = true;
    }

    async loginWithPasskey() {
        try {
            const authenticatorCredential = await this.webauthnService.getCredential();

            if (!authenticatorCredential || authenticatorCredential.type != 'public-key') {
                // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
                throw new InvalidCredentialError();
            }

            const credential = getCredentialWithGracefullyHandlingAuthenticatorIssues(authenticatorCredential) as unknown as PublicKeyCredential;
            if (!credential) {
                // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
                throw new InvalidCredentialError();
            }

            await this.webauthnApiService.loginWithPasskey(credential);
            this.handleLoginSuccess();
        } catch (error) {
            if (error instanceof InvalidCredentialError) {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
            } else {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.login');
            }
            // eslint-disable-next-line no-undef
            console.error(error);
            throw error;
        }
    }

    /**
     * Handle a successful user login.
     */
    private handleLoginSuccess() {
        this.authenticationError = false;
        this.accountService.userIdentity = {
            ...this.accountService.userIdentity,
            isLoggedInWithPasskey: true,
            internal: this.accountService.userIdentity?.internal ?? false,
        };
        this.isLoggedInWithPasskey.set(true); // TODO can be done via effect one other PR is merged
        this.justLoggedInWithPasskey = true;

        if (this.router.url === '/register' || /^\/activate\//.test(this.router.url) || /^\/reset\//.test(this.router.url)) {
            this.router.navigate(['']);
        }

        this.eventManager.broadcast({
            name: 'authenticationSuccess',
            content: 'Sending Authentication Success',
        });
    }
}
