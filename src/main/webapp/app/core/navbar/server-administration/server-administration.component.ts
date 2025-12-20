import { Component, input, output } from '@angular/core';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { faUserShield } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-server-administration',
    imports: [TranslateDirective, FaIconComponent, HasAnyAuthorityDirective, RouterLinkActive, RouterLink],
    templateUrl: './server-administration.component.html',
    styleUrl: '../navbar.scss',
})
export class ServerAdministrationComponent {
    protected readonly faUserShield = faUserShield;

    isExamActive = input<boolean>(false);
    isExamStarted = input<boolean>(false);

    collapseNavbarListener = output<void>();

    collapseNavbar() {
        this.collapseNavbarListener.emit();
    }

    onLinkClick() {
        this.collapseNavbar();
    }
}
