import { Component, OnInit, input, output } from '@angular/core';
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
import { RouterLink, RouterLinkActive } from '@angular/router';

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

    isExamActive = input<boolean>(false);
    isExamStarted = input<boolean>(false);
    localCIActive = input<boolean>(false);
    irisEnabled = input<boolean>(false);
    ltiEnabled = input<boolean>(false);
    standardizedCompetenciesEnabled = input<boolean>(false);
    atlasEnabled = input<boolean>(false);
    examEnabled = input<boolean>(false);

    collapseNavbarListener = output<void>();

    protected collapseNavbar() {
        this.collapseNavbarListener.emit();
    }
}
