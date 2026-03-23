import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faGlobe } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FOOTER_LINK_GROUPS } from 'app/core/landing/landing-data';

@Component({
    selector: 'jhi-landing-footer',
    standalone: true,
    imports: [FaIconComponent, ArtemisTranslatePipe, RouterLink],
    styles: `
        :host {
            display: block;
        }

        .footer {
            background: var(--iris-secondary-background);
            display: flex;
            flex-direction: column;
            gap: 0;
            align-items: center;
            padding-top: 80px;
            padding-bottom: 24px;
        }

        .footer-top {
            display: flex;
            align-items: flex-end;
            justify-content: space-between;
            padding: 40px 160px;
            width: 100%;
        }

        .footer-tagline-section {
            display: flex;
            flex-direction: column;
            gap: 0;
        }

        .footer-tagline {
            font-size: 48px;
            font-weight: 700;
            color: var(--body-color);
            line-height: 1.5;
            max-width: 768px;
            margin: 0;
        }

        .footer-subtitle {
            font-size: 14px;
            font-weight: 400;
            color: var(--text-body-secondary);
            line-height: 1.6;
            margin: 0;
        }

        .cta-btn {
            background: var(--primary);
            color: var(--iris-primary-background);
            border: none;
            padding: 8px 24px;
            border-radius: 8px;
            font-size: 16px;
            font-weight: 500;
            cursor: pointer;
            line-height: 1.6;
            white-space: nowrap;
            transition: opacity 0.2s;
        }

        .cta-btn:hover {
            opacity: 0.9;
        }

        .footer-links {
            display: flex;
            align-items: flex-start;
            justify-content: space-between;
            padding: 40px 160px;
            width: 100%;
        }

        .footer-logo-section {
            display: flex;
            flex-direction: column;
            gap: 16px;
            width: 500px;
        }

        .footer-logo {
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .footer-logo img {
            height: 36px;
            width: 42px;
        }

        .footer-logo-text {
            font-size: 20px;
            font-weight: 700;
            color: var(--text-body-secondary);
            line-height: 1.5;
        }

        .footer-description {
            font-size: 14px;
            font-weight: 400;
            color: var(--text-body-secondary);
            line-height: 1.6;
            max-width: 320px;
            margin: 0;
        }

        .social-links {
            display: flex;
            gap: 16px;
            align-items: center;
            padding: 4px 0;
        }

        .social-link {
            color: var(--text-body-secondary);
            font-size: 20px;
            transition: color 0.2s;
            text-decoration: none;
            display: flex;
            align-items: center;
        }

        .social-link:hover {
            color: var(--primary);
        }

        .social-link svg {
            width: 20px;
            height: 20px;
            fill: currentColor;
        }

        .link-columns {
            display: flex;
            flex: 1;
            justify-content: space-between;
        }

        .link-group {
            display: flex;
            flex-direction: column;
            gap: 16px;
        }

        .link-group-title {
            font-size: 16px;
            font-weight: 500;
            color: var(--body-color);
            line-height: 1.6;
            margin: 0;
        }

        .link-list {
            display: flex;
            flex-direction: column;
            gap: 8px;
        }

        .link-item {
            font-size: 14px;
            font-weight: 400;
            color: var(--text-body-secondary);
            text-decoration: none;
            line-height: 1.6;
            transition: color 0.2s;
        }

        .link-item:hover {
            color: var(--primary);
        }

        .footer-bottom {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 24px 160px;
            width: 100%;
            border-top: 1px solid var(--gray-300);
        }

        .bottom-links {
            display: flex;
            gap: 40px;
            align-items: center;
        }

        .bottom-link {
            font-size: 14px;
            font-weight: 400;
            color: var(--text-body-secondary);
            text-decoration: none;
            line-height: 1.6;
            transition: color 0.2s;
        }

        .bottom-link:hover {
            color: var(--primary);
        }

        .copyright {
            font-size: 14px;
            font-weight: 400;
            color: var(--text-body-secondary);
            line-height: 1.6;
            margin: 0;
        }

        @media (max-width: 1200px) {
            .footer-top,
            .footer-links,
            .footer-bottom {
                padding-left: 40px;
                padding-right: 40px;
            }
        }

        @media (max-width: 768px) {
            .footer-top,
            .footer-links,
            .footer-bottom {
                padding-left: 20px;
                padding-right: 20px;
                flex-direction: column;
                gap: 24px;
                align-items: flex-start;
            }

            .footer-tagline {
                font-size: 32px;
            }

            .footer-logo-section {
                width: 100%;
            }

            .link-columns {
                flex-wrap: wrap;
                gap: 24px;
            }

            .footer-bottom {
                gap: 16px;
            }
        }
    `,
    template: `
        <footer class="footer" id="footer">
            <div class="footer-top">
                <div class="footer-tagline-section">
                    <h2 class="footer-tagline">{{ 'landing.footer.tagline' | artemisTranslate }}</h2>
                    <p class="footer-subtitle">{{ 'landing.footer.subtitle' | artemisTranslate }}</p>
                </div>
                <button class="cta-btn" (click)="navigateToLogin()">{{ 'landing.footer.getStarted' | artemisTranslate }}</button>
            </div>

            <div class="footer-links">
                <div class="footer-logo-section">
                    <div class="footer-logo">
                        <img src="public/images/logo.png" alt="Artemis Logo" />
                        <span class="footer-logo-text">Artemis</span>
                    </div>
                    <p class="footer-description">{{ 'landing.footer.description' | artemisTranslate }}</p>
                    <div class="social-links">
                        <a class="social-link" href="https://artemis.cit.tum.de" target="_blank" rel="noopener" aria-label="Website">
                            <fa-icon [icon]="faGlobe" />
                        </a>
                        <a class="social-link" href="https://www.linkedin.com/company/Artemis-Platform" target="_blank" rel="noopener" aria-label="LinkedIn">
                            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 448 512">
                                <path
                                    d="M416 32H31.9C14.3 32 0 46.5 0 64.3v383.4C0 465.5 14.3 480 31.9 480H416c17.6 0 32-14.5 32-32.3V64.3c0-17.8-14.4-32.3-32-32.3zM135.4 416H69V202.2h66.5V416zm-33.2-243c-21.3 0-38.5-17.3-38.5-38.5S80.9 96 102.2 96c21.2 0 38.5 17.3 38.5 38.5 0 21.3-17.2 38.5-38.5 38.5zm282.1 243h-66.4V312c0-24.8-.5-56.7-34.5-56.7-34.6 0-39.9 27-39.9 54.9V416h-66.4V202.2h63.7v29.2h.9c8.9-16.8 30.6-34.5 62.9-34.5 67.2 0 79.7 44.3 79.7 101.9V416z"
                                />
                            </svg>
                        </a>
                        <a class="social-link" href="https://www.instagram.com/Artemis_edu" target="_blank" rel="noopener" aria-label="Instagram">
                            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 448 512">
                                <path
                                    d="M224.1 141c-63.6 0-114.9 51.3-114.9 114.9s51.3 114.9 114.9 114.9S339 319.5 339 255.9 287.7 141 224.1 141zm0 189.6c-41.1 0-74.7-33.5-74.7-74.7s33.5-74.7 74.7-74.7 74.7 33.5 74.7 74.7-33.6 74.7-74.7 74.7zm146.4-194.3c0 14.9-12 26.8-26.8 26.8-14.9 0-26.8-12-26.8-26.8s12-26.8 26.8-26.8 26.8 12 26.8 26.8zm76.1 27.2c-1.7-35.9-9.9-67.7-36.2-93.9-26.2-26.2-58-34.4-93.9-36.2-37-2.1-147.9-2.1-184.9 0-35.8 1.7-67.6 9.9-93.9 36.1s-34.4 58-36.2 93.9c-2.1 37-2.1 147.9 0 184.9 1.7 35.9 9.9 67.7 36.2 93.9s58 34.4 93.9 36.2c37 2.1 147.9 2.1 184.9 0 35.9-1.7 67.7-9.9 93.9-36.2 26.2-26.2 34.4-58 36.2-93.9 2.1-37 2.1-147.8 0-184.8zM398.8 388c-7.8 19.6-22.9 34.7-42.6 42.6-29.5 11.7-99.5 9-132.1 9s-102.7 2.6-132.1-9c-19.6-7.8-34.7-22.9-42.6-42.6-11.7-29.5-9-99.5-9-132.1s-2.6-102.7 9-132.1c7.8-19.6 22.9-34.7 42.6-42.6 29.5-11.7 99.5-9 132.1-9s102.7-2.6 132.1 9c19.6 7.8 34.7 22.9 42.6 42.6 11.7 29.5 9 99.5 9 132.1s2.7 102.7-9 132.1z"
                                />
                            </svg>
                        </a>
                    </div>
                </div>

                <div class="link-columns">
                    @for (group of linkGroups; track group.titleKey) {
                        <div class="link-group">
                            <p class="link-group-title">{{ group.titleKey | artemisTranslate }}</p>
                            <div class="link-list">
                                @for (link of group.links; track link.labelKey) {
                                    @if (link.href) {
                                        <a class="link-item" [href]="link.href" target="_blank" rel="noopener">{{ link.labelKey | artemisTranslate }}</a>
                                    } @else if (link.routerLink) {
                                        <a class="link-item" [routerLink]="link.routerLink">{{ link.labelKey | artemisTranslate }}</a>
                                    }
                                }
                            </div>
                        </div>
                    }
                </div>
            </div>

            <div class="footer-bottom">
                <div class="bottom-links">
                    <a class="bottom-link" href="mailto:feedback&#64;artemis.cit.tum.de">{{ 'landing.footer.imprint.feedback' | artemisTranslate }}</a>
                    <a class="bottom-link" routerLink="/privacy">{{ 'landing.footer.imprint.privacy' | artemisTranslate }}</a>
                    <a class="bottom-link" routerLink="/imprint">{{ 'landing.footer.imprint.imprint' | artemisTranslate }}</a>
                </div>
                <p class="copyright">{{ 'landing.footer.copyright' | artemisTranslate }}</p>
            </div>
        </footer>
    `,
})
export class LandingFooterComponent {
    protected readonly faGlobe = faGlobe;

    private router = inject(Router);
    linkGroups = FOOTER_LINK_GROUPS;

    navigateToLogin(): void {
        this.router.navigateByUrl('/sign-in');
    }
}
