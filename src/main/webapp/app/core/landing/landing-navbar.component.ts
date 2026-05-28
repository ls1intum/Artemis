import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBars, faFlag, faXmark } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateService } from '@ngx-translate/core';
import { ThemeSwitchComponent } from 'app/core/theme/theme-switch.component';
import { MenuModule } from 'primeng/menu';
import { FindLanguageFromKeyPipe } from 'app/shared/language/find-language-from-key.pipe';
import { LANGUAGES } from 'app/core/language/shared/language.constants';
import { MenuItem } from 'primeng/api';

@Component({
    selector: 'jhi-landing-navbar',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, ArtemisTranslatePipe, ThemeSwitchComponent, MenuModule],
    styles: `
        :host {
            display: block;
            position: sticky;
            top: 0;
            z-index: 1000;
            background-color: var(--artemis-dark);
        }

        .landing-navbar {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 8px 80px;
            max-width: 1600px;
            width: 100%;
            margin: 0 auto;
        }

        .logo {
            display: flex;
            align-items: center;
            gap: 8px;
            text-decoration: none;
            cursor: pointer;
        }

        .logo img {
            height: 30px;
            width: 32px;
            /* Natural asset aspect ratio differs from displayed size; pin it so the browser
               matches natural ratio (Lighthouse "Image aspect ratio" audit). */
            object-fit: contain;
        }

        .logo-text {
            font-size: 18px;
            font-weight: 700;
            color: var(--navbar-foreground);
        }

        .right-section {
            display: flex;
            align-items: center;
            gap: 16px;
        }

        .icon-btn {
            background: none;
            border: none;
            cursor: pointer;
            color: var(--navbar-dark-color);
            padding: 4px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 14px;
            transition: color 0.2s;
        }

        .icon-btn:hover {
            color: var(--navbar-foreground);
        }

        .lang-dropdown {
            display: flex;
            align-items: center;
        }

        .lang-dropdown .nav-link {
            color: var(--navbar-dark-color);
            padding: 0;
            display: flex;
            align-items: center;
            gap: 4px;
            cursor: pointer;
        }

        .lang-dropdown .nav-link:hover {
            color: var(--navbar-foreground);
        }

        /* Darker primary + bold text pushes contrast past WCAG AA (≥ 4.5:1) on the navy navbar. */
        .login-btn {
            background-color: var(--primary-dark, var(--primary));
            color: var(--white);
            border: none;
            padding: 6px 16px;
            border-radius: 8px;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            line-height: 1.6;
            transition:
                background-color 0.2s,
                opacity 0.2s;
        }

        .login-btn:hover {
            background-color: var(--primary);
            opacity: 0.95;
        }

        .login-btn:focus-visible {
            outline: 2px solid var(--white);
            outline-offset: 2px;
        }

        .mobile-menu-btn {
            display: none;
        }

        .mobile-nav {
            display: none;
        }

        @media (max-width: 1024px) {
            .landing-navbar {
                padding: 12px 40px;
            }
        }

        @media (max-width: 768px) {
            .landing-navbar {
                padding: 12px 20px;
            }

            .right-section .lang-dropdown,
            .right-section jhi-theme-switch {
                display: none;
            }

            .mobile-menu-btn {
                display: flex;
            }

            .mobile-nav {
                display: flex;
                flex-direction: column;
                gap: 16px;
                padding: 16px 20px;
                border-top: 1px solid rgba(255, 255, 255, 0.1);
            }

            .mobile-nav .mobile-controls {
                display: flex;
                align-items: center;
                gap: 16px;
            }
        }
    `,
    template: `
        <nav class="landing-navbar">
            <div class="logo" (click)="scrollToTop()">
                <img src="public/images/logo.svg" alt="Artemis Logo" width="32" height="30" />
                <span class="logo-text">Artemis</span>
            </div>

            <div class="right-section">
                <jhi-theme-switch [popoverPlacement]="'bottom-right'" />
                <div class="lang-dropdown">
                    <button type="button" class="nav-link" (click)="langMenu.toggle($event)">
                        <fa-icon [icon]="faFlag" />
                        <span>{{ 'global.menu.language' | artemisTranslate }}</span>
                    </button>
                    <p-menu #langMenu [model]="languageMenuItems" [popup]="true" />
                </div>
                <button class="login-btn" (click)="navigateToLogin()">{{ 'landing.navbar.logIn' | artemisTranslate }}</button>
                <button
                    class="icon-btn mobile-menu-btn"
                    (click)="toggleMobileMenu()"
                    [attr.aria-expanded]="mobileMenuOpen()"
                    [attr.aria-label]="'landing.navbar.toggleMenu' | artemisTranslate"
                >
                    <fa-icon [icon]="mobileMenuOpen() ? faXmark : faBars" />
                </button>
            </div>
        </nav>

        @if (mobileMenuOpen()) {
            <div class="mobile-nav">
                <div class="mobile-controls">
                    <jhi-theme-switch [popoverPlacement]="'bottom'" />
                    <div class="lang-dropdown">
                        <button type="button" class="nav-link" (click)="mobileLangMenu.toggle($event)">
                            <fa-icon [icon]="faFlag" />
                            <span>{{ 'global.menu.language' | artemisTranslate }}</span>
                        </button>
                        <p-menu #mobileLangMenu [model]="mobileLanguageMenuItems" [popup]="true" />
                    </div>
                </div>
            </div>
        }
    `,
})
export class LandingNavbarComponent {
    protected readonly faBars = faBars;
    protected readonly faXmark = faXmark;
    protected readonly faFlag = faFlag;

    private translateService = inject(TranslateService);
    private findLanguagePipe = new FindLanguageFromKeyPipe();
    private router = inject(Router);

    languages = LANGUAGES;
    mobileMenuOpen = signal(false);

    languageMenuItems: MenuItem[] = this.languages.map((lang) => ({
        label: this.findLanguagePipe.transform(lang),
        command: () => this.changeLanguage(lang),
    }));

    mobileLanguageMenuItems: MenuItem[] = this.languages.map((lang) => ({
        label: this.findLanguagePipe.transform(lang),
        command: () => {
            this.changeLanguage(lang);
            this.toggleMobileMenu();
        },
    }));

    changeLanguage(languageKey: string): void {
        this.translateService.use(languageKey);
    }

    toggleMobileMenu(): void {
        this.mobileMenuOpen.update((open) => !open);
    }

    navigateToLogin(): void {
        this.router.navigateByUrl('/sign-in');
    }

    scrollToTop(): void {
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }
}
