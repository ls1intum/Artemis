import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faLink } from '@fortawesome/free-solid-svg-icons';
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
            background: var(--artemis-dark);
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
            padding: 40px 80px;
            max-width: 1600px;
            width: 100%;
            margin: 0 auto;
        }

        .footer-tagline-section {
            display: flex;
            flex-direction: column;
            gap: 0;
        }

        .footer-tagline {
            font-size: 48px;
            font-weight: 700;
            color: var(--white);
            line-height: 1.5;
            max-width: 768px;
            margin: 0;
        }

        .footer-subtitle {
            font-size: 16px;
            font-weight: 400;
            color: var(--navbar-dark-color);
            line-height: 1.6;
            margin: 0;
        }

        .cta-btn {
            background: var(--primary);
            color: #fff;
            border: none;
            padding: 8px 16px;
            border-radius: 8px;
            font-size: 14px;
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
            padding: 40px 80px;
            max-width: 1600px;
            width: 100%;
            margin: 0 auto;
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
            height: 30px;
            width: 32px;
        }

        .footer-logo-text {
            font-size: 18px;
            font-weight: 700;
            color: var(--navbar-foreground);
        }

        .footer-description {
            font-size: 14px;
            font-weight: 400;
            color: var(--navbar-dark-color);
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
            color: var(--navbar-foreground);
            font-size: 16px;
            transition: color 0.2s;
            text-decoration: none;
            display: flex;
            align-items: center;
        }

        .social-icon {
            width: 1em;
            height: 1em;
            fill: currentColor;
        }

        .social-link:hover {
            color: var(--primary);
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
            font-size: 14px;
            font-weight: 500;
            color: var(--navbar-foreground);
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
            color: var(--navbar-dark-color);
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
            padding: 16px 80px;
            max-width: 1600px;
            width: 100%;
            margin: 0 auto;
        }

        .bottom-links {
            display: flex;
            gap: 40px;
            align-items: center;
        }

        .bottom-link {
            font-size: 14px;
            font-weight: 400;
            color: var(--navbar-foreground);
            text-decoration: none;
            transition: color 0.2s;
        }

        .bottom-link:hover {
            color: var(--primary);
        }

        .copyright {
            font-size: 14px;
            font-weight: 400;
            color: var(--navbar-foreground);
            line-height: 1.6;
            margin: 0;
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
                            <fa-icon [icon]="faLink" />
                        </a>
                        <a class="social-link" href="https://www.linkedin.com/company/tumaet" target="_blank" rel="noopener" aria-label="LinkedIn">
                            <svg class="social-icon" viewBox="0 0 448 512" aria-hidden="true">
                                <path
                                    d="M100.28 448H7.4V148.9h92.88zM53.79 108.1C24.09 108.1 0 83.5 0 53.8a53.79 53.79 0 0 1 107.58 0c0 29.7-24.1 54.3-53.79 54.3zM447.9 448h-92.68V302.4c0-34.7-.7-79.2-48.29-79.2-48.3 0-55.69 37.7-55.69 76.7V448h-92.78V148.9h89.08v40.8h1.3c12.4-23.5 42.69-48.3 87.88-48.3 94 0 111.28 61.9 111.28 142.3V448z"
                                />
                            </svg>
                        </a>
                        <a class="social-link" href="https://www.instagram.com/tum.aet" target="_blank" rel="noopener" aria-label="Instagram">
                            <svg class="social-icon" viewBox="0 0 448 512" aria-hidden="true">
                                <path
                                    d="M224.1 141c-63.6 0-114.9 51.3-114.9 114.9s51.3 114.9 114.9 114.9S339 319.5 339 255.9 287.7 141 224.1 141m0 189.6c-41.3 0-74.7-33.4-74.7-74.7s33.4-74.7 74.7-74.7 74.7 33.4 74.7 74.7-33.5 74.7-74.7 74.7M370.5 136.7c0 14.9-12 26.8-26.8 26.8-14.9 0-26.8-12-26.8-26.8 0-14.9 12-26.8 26.8-26.8 14.8 0 26.8 12 26.8 26.8m76.1 27.2c-1.7-35.3-9.7-66.6-35.6-92.4S354.2 37.5 318.9 35.8c-35.4-2-141.5-2-176.9 0-35.3 1.7-66.6 9.7-92.4 35.6S11.8 128.2 10.1 163.5c-2 35.4-2 141.5 0 176.9 1.7 35.3 9.7 66.6 35.6 92.4s57.1 33.9 92.4 35.6c35.4 2 141.5 2 176.9 0 35.3-1.7 66.6-9.7 92.4-35.6s33.9-57.1 35.6-92.4c2-35.4 2-141.4 0-176.9m-47.8 214.5c-1.5 31.5-7.3 48.6-18.3 59.6s-28.1 16.8-59.6 18.3c-31.8 1.8-127.2 1.8-159 0-31.5-1.5-48.6-7.3-59.6-18.3s-16.8-28.1-18.3-59.6c-1.8-31.8-1.8-127.2 0-159 1.5-31.5 7.3-48.6 18.3-59.6s28.1-16.8 59.6-18.3c31.8-1.8 127.2-1.8 159 0 31.5 1.5 48.6 7.3 59.6 18.3s16.8 28.1 18.3 59.6c1.8 31.8 1.8 127.1 0 159"
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
                    <a class="bottom-link" routerLink="/about">{{ 'landing.footer.imprint.about' | artemisTranslate }}</a>
                    <a class="bottom-link" href="https://github.com/ls1intum/Artemis/releases" target="_blank" rel="noopener">{{
                        'landing.footer.imprint.releases' | artemisTranslate
                    }}</a>
                    <a class="bottom-link" href="https://github.com/ls1intum/Artemis/issues/new/choose" target="_blank" rel="noopener">{{
                        'landing.footer.imprint.feedback' | artemisTranslate
                    }}</a>
                    <a class="bottom-link" routerLink="/privacy">{{ 'landing.footer.imprint.privacy' | artemisTranslate }}</a>
                    <a class="bottom-link" routerLink="/imprint">{{ 'landing.footer.imprint.imprint' | artemisTranslate }}</a>
                </div>
                <p class="copyright">{{ 'landing.footer.copyright' | artemisTranslate }}</p>
            </div>
        </footer>
    `,
})
export class LandingFooterComponent {
    protected readonly faLink = faLink;

    private router = inject(Router);
    linkGroups = FOOTER_LINK_GROUPS;

    navigateToLogin(): void {
        this.router.navigateByUrl('/sign-in');
    }
}
