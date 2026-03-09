import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-landing-navbar',
    standalone: true,
    imports: [RouterLink, TranslateDirective],
    template: `
        <nav class="landing-nav">
            <div class="nav-container">
                <a routerLink="/" class="nav-brand">
                    <img src="content/images/landing/logo.png" alt="Artemis" class="nav-logo" />
                    <span class="nav-brand-text">Artemis</span>
                </a>

                <!-- Desktop nav -->
                <div class="nav-links">
                    <a href="#features" class="nav-link" jhiTranslate="landing.navbar.features"></a>
                    <a href="#programming-exercises" class="nav-link" jhiTranslate="landing.navbar.programmingExercises"></a>
                    <a href="#exams" class="nav-link" jhiTranslate="landing.navbar.exams"></a>
                </div>

                <div class="nav-right">
                    <a routerLink="/sign-in" class="sign-in-btn" jhiTranslate="landing.navbar.signIn"></a>
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
                    <a href="#features" class="mobile-link" (click)="closeMenu()" jhiTranslate="landing.navbar.features"></a>
                    <a href="#programming-exercises" class="mobile-link" (click)="closeMenu()" jhiTranslate="landing.navbar.programmingExercises"></a>
                    <a href="#exams" class="mobile-link" (click)="closeMenu()" jhiTranslate="landing.navbar.exams"></a>
                    <a routerLink="/sign-in" class="mobile-link sign-in" (click)="closeMenu()" jhiTranslate="landing.navbar.signIn"></a>
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
            text-decoration: none;
            color: white;
        }

        .nav-logo {
            height: 32px;
            width: auto;
        }

        .nav-brand-text {
            font-size: 1.25rem;
            font-weight: 700;
        }

        .nav-links {
            display: flex;
            gap: 2rem;
            position: absolute;
            left: 50%;
            transform: translateX(-50%);
        }

        .nav-link {
            color: #94a3b8;
            text-decoration: none;
            font-size: 0.875rem;
            font-weight: 500;
            transition: color 0.2s;

            &:hover {
                color: white;
            }
        }

        .nav-right {
            display: flex;
            align-items: center;
            gap: 0.75rem;
        }

        .sign-in-btn {
            background: white;
            color: black;
            padding: 0.5rem 1rem;
            border-radius: 0.375rem;
            font-weight: 600;
            font-size: 0.875rem;
            text-decoration: none;
            transition:
                background 0.2s,
                transform 0.2s;

            &:hover {
                background: #e2e8f0;
                transform: translateY(-1px);
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

        @media (max-width: 768px) {
            .nav-links {
                display: none;
            }

            .sign-in-btn {
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
    menuOpen = signal(false);

    toggleMenu(): void {
        this.menuOpen.update((v) => !v);
    }

    closeMenu(): void {
        this.menuOpen.set(false);
    }
}
