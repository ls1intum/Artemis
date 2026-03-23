import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBars, faMoon, faSun, faXmark } from '@fortawesome/free-solid-svg-icons';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-landing-navbar',
    standalone: true,
    imports: [FaIconComponent, ArtemisTranslatePipe],
    styles: `
        :host {
            display: block;
            position: sticky;
            top: 0;
            z-index: 1000;
        }

        .landing-navbar {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 12px 160px;
            background: color-mix(in srgb, var(--iris-primary-background) 85%, transparent);
            backdrop-filter: blur(12px);
            -webkit-backdrop-filter: blur(12px);
        }

        .logo {
            display: flex;
            align-items: center;
            gap: 8px;
            text-decoration: none;
            cursor: pointer;
        }

        .logo img {
            height: 36px;
            width: 42px;
        }

        .logo-text {
            font-size: 20px;
            font-weight: 700;
            color: var(--text-body-secondary);
            line-height: 1.5;
        }

        .nav-links {
            display: flex;
            gap: 40px;
            align-items: center;
        }

        .nav-link {
            font-size: 16px;
            font-weight: 500;
            color: var(--body-color);
            text-decoration: none;
            cursor: pointer;
            background: none;
            border: none;
            padding: 0;
            line-height: 1.6;
            transition: color 0.2s;
        }

        .nav-link:hover {
            color: var(--primary);
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
            color: var(--body-color);
            padding: 4px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 18px;
            transition: color 0.2s;
        }

        .icon-btn:hover {
            color: var(--primary);
        }

        .lang-btn {
            font-size: 16px;
            font-weight: 500;
            color: var(--body-color);
            background: none;
            border: none;
            cursor: pointer;
            padding: 0;
            line-height: 1.6;
        }

        .login-btn {
            background: var(--primary);
            color: var(--iris-primary-background);
            border: none;
            padding: 8px 24px;
            border-radius: 8px;
            font-size: 16px;
            font-weight: 500;
            cursor: pointer;
            line-height: 1.6;
            transition: opacity 0.2s;
        }

        .login-btn:hover {
            opacity: 0.9;
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

            .nav-links {
                display: none;
            }

            .right-section .lang-btn,
            .right-section .icon-btn:first-child {
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
                background: var(--iris-primary-background);
                border-top: 1px solid var(--gray-300);
            }

            .mobile-nav .nav-link {
                font-size: 18px;
                padding: 8px 0;
            }
        }
    `,
    template: `
        <nav class="landing-navbar">
            <div class="logo" (click)="scrollToTop()">
                <img src="public/images/logo.png" alt="Artemis Logo" />
                <span class="logo-text">Artemis</span>
            </div>

            <div class="nav-links">
                <button class="nav-link" (click)="scrollTo('hero')">{{ 'landing.navbar.home' | artemisTranslate }}</button>
                <button class="nav-link" (click)="scrollTo('features')">{{ 'landing.navbar.features' | artemisTranslate }}</button>
                <button class="nav-link" (click)="scrollTo('faq')">{{ 'landing.navbar.resources' | artemisTranslate }}</button>
                <button class="nav-link" (click)="scrollTo('footer')">{{ 'landing.navbar.contact' | artemisTranslate }}</button>
            </div>

            <div class="right-section">
                <button class="icon-btn" (click)="toggleTheme()" [attr.aria-label]="themeAriaLabel()">
                    <fa-icon [icon]="isDark() ? faSun : faMoon" />
                </button>
                <button class="lang-btn" (click)="toggleLanguage()">{{ currentLang().toUpperCase() }}</button>
                <button class="login-btn" (click)="navigateToLogin()">{{ 'landing.navbar.logIn' | artemisTranslate }}</button>
                <button class="icon-btn mobile-menu-btn" (click)="toggleMobileMenu()" [attr.aria-expanded]="mobileMenuOpen()" aria-label="Toggle menu">
                    <fa-icon [icon]="mobileMenuOpen() ? faXmark : faBars" />
                </button>
            </div>
        </nav>

        @if (mobileMenuOpen()) {
            <div class="mobile-nav">
                <button class="nav-link" (click)="scrollTo('hero'); toggleMobileMenu()">{{ 'landing.navbar.home' | artemisTranslate }}</button>
                <button class="nav-link" (click)="scrollTo('features'); toggleMobileMenu()">{{ 'landing.navbar.features' | artemisTranslate }}</button>
                <button class="nav-link" (click)="scrollTo('faq'); toggleMobileMenu()">{{ 'landing.navbar.resources' | artemisTranslate }}</button>
                <button class="nav-link" (click)="scrollTo('footer'); toggleMobileMenu()">{{ 'landing.navbar.contact' | artemisTranslate }}</button>
                <button class="icon-btn" (click)="toggleTheme()" [attr.aria-label]="themeAriaLabel()">
                    <fa-icon [icon]="isDark() ? faSun : faMoon" />
                </button>
                <button class="lang-btn" (click)="toggleLanguage()">{{ currentLang().toUpperCase() }}</button>
            </div>
        }
    `,
})
export class LandingNavbarComponent {
    protected readonly faSun = faSun;
    protected readonly faMoon = faMoon;
    protected readonly faBars = faBars;
    protected readonly faXmark = faXmark;

    private themeService = inject(ThemeService);
    private translateService = inject(TranslateService);
    private router = inject(Router);

    mobileMenuOpen = signal(false);
    currentLang = signal(this.translateService.getCurrentLang() || 'en');
    isDark = computed(() => this.themeService.currentTheme() === Theme.DARK);
    themeAriaLabel = computed(() => (this.isDark() ? 'Switch to light mode' : 'Switch to dark mode'));

    toggleTheme(): void {
        const newTheme = this.isDark() ? Theme.LIGHT : Theme.DARK;
        this.themeService.applyThemePreference(newTheme);
    }

    toggleLanguage(): void {
        const newLang = this.currentLang() === 'en' ? 'de' : 'en';
        this.translateService.use(newLang);
        this.currentLang.set(newLang);
    }

    toggleMobileMenu(): void {
        this.mobileMenuOpen.update((open) => !open);
    }

    navigateToLogin(): void {
        this.router.navigateByUrl('/sign-in');
    }

    scrollTo(sectionId: string): void {
        document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth' });
    }

    scrollToTop(): void {
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }
}
