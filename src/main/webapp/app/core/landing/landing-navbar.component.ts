import { Component, computed, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MenuItem } from 'primeng/api';
import { Menu, MenuModule } from 'primeng/menu';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-landing-navbar',
    standalone: true,
    imports: [TranslateDirective, MenuModule],
    template: `
        <nav class="landing-nav">
            <div class="nav-container">
                <span class="nav-brand" (click)="scrollTo('top')">
                    <img src="content/images/landing/logo.png" alt="Artemis" class="nav-logo" />
                    <span class="nav-brand-text">Artemis</span>
                </span>

                <!-- Desktop nav -->
                <div class="landing-links">
                    <span class="landing-link" (click)="scrollTo('features')" jhiTranslate="landing.navbar.features"></span>
                    <button class="landing-link dropdown-trigger" (click)="toggleDocsMenu($event)">
                        <span jhiTranslate="landing.navbar.documentation"></span>
                        <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <polyline points="6 9 12 15 18 9" />
                        </svg>
                    </button>
                    <p-menu #docsMenu [model]="docsMenuItems" [popup]="true" appendTo="body" styleClass="landing-docs-menu" />
                </div>

                <div class="nav-right">
                    <div class="lang-switch">
                        <button class="lang-btn" [class.active]="isEnglish()" (click)="changeLanguage('en')">EN</button>
                        <span class="lang-sep">/</span>
                        <button class="lang-btn" [class.active]="!isEnglish()" (click)="changeLanguage('de')">DE</button>
                    </div>
                    <button class="sign-in-btn" (click)="navigateToSignIn()" jhiTranslate="landing.navbar.signIn"></button>
                    <!-- Hamburger -->
                    <button class="hamburger" (click)="toggleMenu()" [attr.aria-expanded]="menuOpen()" aria-label="Toggle navigation menu">
                        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            @if (!menuOpen()) {
                                <line x1="3" y1="6" x2="21" y2="6" />
                                <line x1="3" y1="12" x2="21" y2="12" />
                                <line x1="3" y1="18" x2="21" y2="18" />
                            } @else {
                                <line x1="18" y1="6" x2="6" y2="18" />
                                <line x1="6" y1="6" x2="18" y2="18" />
                            }
                        </svg>
                    </button>
                </div>
            </div>

            <!-- Mobile menu -->
            @if (menuOpen()) {
                <div class="mobile-menu">
                    <span class="mobile-link" (click)="scrollTo('features'); closeMenu()" jhiTranslate="landing.navbar.features"></span>
                    <div class="mobile-docs">
                        <span class="mobile-link mobile-docs-label" jhiTranslate="landing.navbar.documentation"></span>
                        <div class="mobile-docs-links">
                            <a
                                href="https://docs.artemis.tum.de/user/student-guides/intro"
                                target="_blank"
                                rel="noopener"
                                class="mobile-link mobile-doc-item"
                                (click)="closeMenu()"
                                jhiTranslate="landing.navbar.docStudent"
                            ></a>
                            <a
                                href="https://docs.artemis.tum.de/user/instructor-guides/intro"
                                target="_blank"
                                rel="noopener"
                                class="mobile-link mobile-doc-item"
                                (click)="closeMenu()"
                                jhiTranslate="landing.navbar.docInstructor"
                            ></a>
                            <a
                                href="https://docs.artemis.tum.de/admin/intro"
                                target="_blank"
                                rel="noopener"
                                class="mobile-link mobile-doc-item"
                                (click)="closeMenu()"
                                jhiTranslate="landing.navbar.docAdmin"
                            ></a>
                            <a
                                href="https://docs.artemis.tum.de/dev/intro"
                                target="_blank"
                                rel="noopener"
                                class="mobile-link mobile-doc-item"
                                (click)="closeMenu()"
                                jhiTranslate="landing.navbar.docDeveloper"
                            ></a>
                        </div>
                    </div>
                    <div class="mobile-link mobile-lang">
                        <button class="lang-btn" [class.active]="isEnglish()" (click)="changeLanguage('en')">EN</button>
                        <span class="lang-sep">/</span>
                        <button class="lang-btn" [class.active]="!isEnglish()" (click)="changeLanguage('de')">DE</button>
                    </div>
                    <button class="mobile-link sign-in" (click)="navigateToSignIn(); closeMenu()" jhiTranslate="landing.navbar.signIn"></button>
                </div>
            }
        </nav>
    `,
    styles: `
        .landing-nav {
            position: sticky;
            top: 0;
            z-index: 50;
            background: rgba(0, 0, 0, 0.8);
            backdrop-filter: blur(12px);
            -webkit-backdrop-filter: blur(12px);
            border-bottom: 1px solid #1e293b;
        }

        .nav-container {
            max-width: 1280px;
            margin: 0 auto;
            padding: 0.75rem 1.25rem;
            display: flex;
            align-items: center;
            justify-content: space-between;
            position: relative;
        }

        .nav-brand {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            color: white;
            cursor: pointer;
        }

        .nav-logo {
            height: 32px;
            width: auto;
        }

        .nav-brand-text {
            font-size: 1.25rem;
            font-weight: 700;
        }

        .landing-links {
            display: flex;
            gap: 2rem;
            position: absolute;
            left: 50%;
            transform: translateX(-50%);
        }

        .landing-link {
            color: #94a3b8;
            text-decoration: none;
            font-size: 0.875rem;
            font-weight: 500;
            cursor: pointer;
            transition: color 0.2s;

            &:hover {
                color: white;
            }
        }

        .dropdown-trigger {
            display: flex;
            align-items: center;
            gap: 0.25rem;
            background: none;
            border: none;
            cursor: pointer;
            padding: 0;
        }

        .nav-right {
            display: flex;
            align-items: center;
            gap: 0.75rem;
        }

        .lang-switch {
            display: flex;
            align-items: center;
            gap: 0.25rem;
        }

        .lang-btn {
            background: none;
            border: none;
            color: #64748b;
            font-size: 0.8125rem;
            font-weight: 500;
            cursor: pointer;
            padding: 0;
            transition: color 0.2s;

            &:hover {
                color: #94a3b8;
            }

            &.active {
                color: white;
                font-weight: 700;
            }
        }

        .lang-sep {
            color: #475569;
            font-size: 0.8125rem;
        }

        .mobile-lang {
            display: flex;
            align-items: center;
            gap: 0.25rem;
            border-bottom: none;
        }

        .sign-in-btn {
            background: white;
            color: black;
            padding: 0.5rem 1rem;
            border: none;
            border-radius: 0.375rem;
            font-weight: 600;
            font-size: 0.875rem;
            cursor: pointer;
            transition: background 0.2s;

            &:hover {
                background: #e2e8f0;
            }
        }

        .hamburger {
            display: none;
            background: none;
            border: none;
            color: white;
            cursor: pointer;
            padding: 0.25rem;
        }

        .mobile-menu {
            display: none;
            padding: 1rem 1.25rem;
            border-top: 1px solid #1e293b;
        }

        .mobile-link {
            display: block;
            color: #94a3b8;
            text-decoration: none;
            padding: 0.75rem 0;
            font-size: 0.875rem;
            font-weight: 500;
            border-bottom: 1px solid #1e293b;
            transition: color 0.2s;

            &:hover {
                color: white;
            }

            &.sign-in {
                color: white;
                font-weight: 600;
                border-bottom: none;
            }
        }

        .mobile-docs {
            border-bottom: 1px solid #1e293b;
        }

        .mobile-docs-label {
            border-bottom: none;
            padding-bottom: 0.25rem;
        }

        .mobile-docs-links {
            padding-left: 1rem;
        }

        .mobile-doc-item {
            border-bottom: none;
            padding: 0.5rem 0;
        }

        @media (max-width: 768px) {
            .landing-links {
                display: none;
            }

            .sign-in-btn {
                display: none;
            }

            .lang-switch {
                display: none;
            }

            .hamburger {
                display: block;
            }

            .mobile-menu {
                display: block;
            }
        }
    `,
})
export class LandingNavbarComponent {
    private readonly router = inject(Router);
    private readonly translateService = inject(TranslateService);
    private readonly docsMenu = viewChild<Menu>('docsMenu');

    menuOpen = signal(false);
    currentLang = signal(inject(TranslateService).currentLang || 'en');
    isEnglish = computed(() => this.currentLang() === 'en');

    docsMenuItems: MenuItem[] = [
        { label: 'Student', url: 'https://docs.artemis.tum.de/user/student-guides/intro', target: '_blank' },
        { label: 'Instructor', url: 'https://docs.artemis.tum.de/user/instructor-guides/intro', target: '_blank' },
        { label: 'Admin', url: 'https://docs.artemis.tum.de/admin/intro', target: '_blank' },
        { label: 'Developer', url: 'https://docs.artemis.tum.de/dev/intro', target: '_blank' },
    ];

    toggleMenu(): void {
        this.menuOpen.update((v) => !v);
    }

    closeMenu(): void {
        this.menuOpen.set(false);
    }

    toggleDocsMenu(event: Event): void {
        this.docsMenu()?.toggle(event);
    }

    scrollTo(id: string): void {
        const scrollContainer = document.querySelector('.page-wrapper');
        if (!scrollContainer) return;
        if (id === 'top') {
            scrollContainer.scrollTo({ top: 0, behavior: 'smooth' });
            return;
        }
        const element = document.getElementById(id);
        if (element) {
            const navbarHeight = 180;
            const top = element.getBoundingClientRect().top + scrollContainer.scrollTop - navbarHeight;
            scrollContainer.scrollTo({ top, behavior: 'smooth' });
        }
    }

    navigateToSignIn(): void {
        this.router.navigateByUrl('/sign-in');
    }

    changeLanguage(lang: string): void {
        this.translateService.use(lang);
        this.currentLang.set(lang);
    }
}
