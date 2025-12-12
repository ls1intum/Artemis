import { Component, effect, inject, input, output, signal, viewChild } from '@angular/core';
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
    faPlug,
    faPuzzlePiece,
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
import { RouterLink, RouterLinkActive } from '@angular/router';
import { LoginWithPasskeyModalComponent } from 'app/core/navbar/server-administration/login-with-passkey-modal/login-with-passkey-modal.component';
import { AccountService } from 'app/core/auth/account.service';
import { PasskeyAuthenticationGuard } from 'app/core/auth/passkey-authentication-guard/passkey-authentication.guard';

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
        LoginWithPasskeyModalComponent,
    ],
    templateUrl: './server-administration.component.html',
    styleUrl: '../navbar.scss',
})
export class ServerAdministrationComponent {
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
    protected readonly faList = faList;
    protected readonly faBell = faBell;
    protected readonly faLock = faLock;
    protected readonly faEye = faEye;
    protected readonly faUser = faUser;
    protected readonly faUserPlus = faUserPlus;
    protected readonly faPlug = faPlug;

    private readonly accountService = inject(AccountService);
    private readonly isLoggedInWithPasskeyGuard = inject(PasskeyAuthenticationGuard);

    adminMenuDropdown = viewChild.required<NgbDropdown>('adminMenuDropdown');
    loginWithPasskeyModal = viewChild.required<LoginWithPasskeyModalComponent>(LoginWithPasskeyModalComponent);

    isExamActive = input<boolean>(false);
    isExamStarted = input<boolean>(false);
    localCIActive = input<boolean>(false);
    ltiEnabled = input<boolean>(false);
    standardizedCompetenciesEnabled = input<boolean>(false);
    atlasEnabled = input<boolean>(false);
    examEnabled = input<boolean>(false);

    collapseNavbarListener = output<void>();

    private justLoggedInWithPasskey = signal(false);

    constructor() {
        effect(() => {
            this.openDropdownIfUserLoggedInWithPasskey();
        });
    }

    private openDropdownIfUserLoggedInWithPasskey() {
        if (this.accountService.isLoggedInWithPasskey() && this.justLoggedInWithPasskey()) {
            this.justLoggedInWithPasskey.set(false);

            // Use setTimeout to wait for the next JavaScript tick (macrotask).
            // This allows Angular to finish re-rendering the dropdown's
            // new content *before* we try to open and position it.
            setTimeout(() => {
                this.adminMenuDropdown().open();
            }, 0);
        }
    }

    collapseNavbar() {
        this.collapseNavbarListener.emit();
    }

    protected showModalForPasskeyLogin() {
        if (!this.isLoggedInWithPasskeyGuard.shouldEnforcePasskeyForAdminFeatures()) {
            return;
        }
        if (this.accountService.isUserLoggedInWithApprovedPasskey()) {
            return;
        }

        this.adminMenuDropdown().close();
        this.loginWithPasskeyModal().showModal = true;
    }

    onJustLoggedInWithPasskey(value: boolean) {
        this.justLoggedInWithPasskey.set(value);
    }
}
