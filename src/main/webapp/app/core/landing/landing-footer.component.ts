import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faLink } from '@fortawesome/free-solid-svg-icons';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FOOTER_LINK_GROUPS, FooterLinkGroup } from 'app/core/landing/landing-data';

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
            color: var(--white);
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
            max-width: 720px;
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
            flex-wrap: wrap;
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

            .bottom-links {
                gap: 16px;
                justify-content: center;
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
                        <a class="social-link" href="https://artemis.cit.tum.de" target="_blank" rel="noopener" [attr.aria-label]="'landing.footer.website' | artemisTranslate">
                            <fa-icon [icon]="faLink" />
                        </a>
                        <a
                            class="social-link"
                            href="https://www.linkedin.com/company/tumaet"
                            target="_blank"
                            rel="noopener"
                            [attr.aria-label]="'landing.footer.linkedin' | artemisTranslate"
                        >
                            <svg class="social-icon" viewBox="0 0 20 20" aria-hidden="true">
                                <path
                                    d="M0 1.4325C0 0.64125 0.6575 0 1.46875 0H18.5312C19.3425 0 20 0.64125 20 1.4325V18.5675C20 19.3588 19.3425 20 18.5312 20H1.46875C0.6575 20 0 19.3588 0 18.5675V1.4325ZM6.17875 16.7425V7.71125H3.1775V16.7425H6.17875ZM4.67875 6.4775C5.725 6.4775 6.37625 5.785 6.37625 4.9175C6.3575 4.03125 5.72625 3.3575 4.69875 3.3575C3.67125 3.3575 3 4.0325 3 4.9175C3 5.785 3.65125 6.4775 4.65875 6.4775H4.67875V6.4775ZM10.8138 16.7425V11.6988C10.8138 11.4288 10.8337 11.1587 10.9137 10.9662C11.13 10.4275 11.6238 9.86875 12.4538 9.86875C13.54 9.86875 13.9737 10.6963 13.9737 11.9113V16.7425H16.975V11.5625C16.975 8.7875 15.495 7.4975 13.52 7.4975C11.9275 7.4975 11.2138 8.3725 10.8138 8.98875V9.02H10.7938C10.8004 9.00957 10.8071 8.99915 10.8138 8.98875V7.71125H7.81375C7.85125 8.55875 7.81375 16.7425 7.81375 16.7425H10.8138Z"
                                />
                            </svg>
                        </a>
                        <a
                            class="social-link"
                            href="https://www.instagram.com/tum.aet"
                            target="_blank"
                            rel="noopener"
                            [attr.aria-label]="'landing.footer.instagram' | artemisTranslate"
                        >
                            <svg class="social-icon" viewBox="0 0 20 20" aria-hidden="true">
                                <path
                                    d="M10 0C7.28625 0 6.945 0.0125 5.87875 0.06C4.8125 0.11 4.08625 0.2775 3.45 0.525C2.78262 0.776024 2.17811 1.16978 1.67875 1.67875C1.1701 2.17837 0.776384 2.7828 0.525 3.45C0.2775 4.085 0.10875 4.8125 0.06 5.875C0.0125 6.94375 0 7.28375 0 10.0013C0 12.7163 0.0125 13.0563 0.06 14.1225C0.11 15.1875 0.2775 15.9137 0.525 16.55C0.78125 17.2075 1.1225 17.765 1.67875 18.3212C2.23375 18.8775 2.79125 19.22 3.44875 19.475C4.08625 19.7225 4.81125 19.8912 5.87625 19.94C6.94375 19.9875 7.28375 20 10 20C12.7163 20 13.055 19.9875 14.1225 19.94C15.1863 19.89 15.915 19.7225 16.5513 19.475C17.2182 19.2239 17.8223 18.8301 18.3212 18.3212C18.8775 17.765 19.2187 17.2075 19.475 16.55C19.7212 15.9137 19.89 15.1875 19.94 14.1225C19.9875 13.0563 20 12.7163 20 10C20 7.28375 19.9875 6.94375 19.94 5.87625C19.89 4.8125 19.7212 4.085 19.475 3.45C19.2237 2.78278 18.8299 2.17834 18.3212 1.67875C17.822 1.16959 17.2175 0.775807 16.55 0.525C15.9125 0.2775 15.185 0.10875 14.1212 0.06C13.0537 0.0125 12.715 0 9.9975 0H10.0013H10ZM9.10375 1.8025H10.0013C12.6713 1.8025 12.9875 1.81125 14.0412 1.86C15.0162 1.90375 15.5462 2.0675 15.8987 2.20375C16.365 2.385 16.6987 2.6025 17.0487 2.9525C17.3987 3.3025 17.615 3.635 17.7963 4.1025C17.9338 4.45375 18.0963 4.98375 18.14 5.95875C18.1888 7.0125 18.1988 7.32875 18.1988 9.9975C18.1988 12.6663 18.1888 12.9837 18.14 14.0375C18.0963 15.0125 17.9325 15.5413 17.7963 15.8938C17.6359 16.3279 17.38 16.7205 17.0475 17.0425C16.6975 17.3925 16.365 17.6088 15.8975 17.79C15.5475 17.9275 15.0175 18.09 14.0412 18.135C12.9875 18.1825 12.6713 18.1938 10.0013 18.1938C7.33125 18.1938 7.01375 18.1825 5.96 18.135C4.985 18.09 4.45625 17.9275 4.10375 17.79C3.66937 17.6299 3.27641 17.3745 2.95375 17.0425C2.62094 16.72 2.36465 16.3271 2.20375 15.8925C2.0675 15.5412 1.90375 15.0113 1.86 14.0363C1.8125 12.9825 1.8025 12.6662 1.8025 9.995C1.8025 7.325 1.8125 7.01 1.86 5.95625C1.905 4.98125 2.0675 4.45125 2.205 4.09875C2.38625 3.6325 2.60375 3.29875 2.95375 2.94875C3.30375 2.59875 3.63625 2.3825 4.10375 2.20125C4.45625 2.06375 4.985 1.90125 5.96 1.85625C6.8825 1.81375 7.24 1.80125 9.10375 1.8V1.8025V1.8025ZM15.3387 3.4625C15.1812 3.4625 15.0251 3.49354 14.8795 3.55384C14.7339 3.61415 14.6017 3.70254 14.4902 3.81397C14.3788 3.9254 14.2904 4.05769 14.2301 4.20328C14.1698 4.34887 14.1387 4.50491 14.1387 4.6625C14.1387 4.82009 14.1698 4.97613 14.2301 5.12172C14.2904 5.26731 14.3788 5.3996 14.4902 5.51103C14.6017 5.62246 14.7339 5.71085 14.8795 5.77116C15.0251 5.83146 15.1812 5.8625 15.3387 5.8625C15.657 5.8625 15.9622 5.73607 16.1873 5.51103C16.4123 5.28598 16.5387 4.98076 16.5387 4.6625C16.5387 4.34424 16.4123 4.03902 16.1873 3.81397C15.9622 3.58893 15.657 3.4625 15.3387 3.4625V3.4625ZM10.0013 4.865C9.32009 4.85437 8.64362 4.97936 8.01122 5.23268C7.37883 5.486 6.80314 5.86259 6.31769 6.34053C5.83223 6.81847 5.44671 7.38821 5.18355 8.01657C4.9204 8.64494 4.78488 9.31938 4.78488 10.0006C4.78488 10.6819 4.9204 11.3563 5.18355 11.9847C5.44671 12.613 5.83223 13.1828 6.31769 13.6607C6.80314 14.1387 7.37883 14.5153 8.01122 14.7686C8.64362 15.0219 9.32009 15.1469 10.0013 15.1363C11.3494 15.1152 12.6353 14.5649 13.5812 13.6041C14.5272 12.6432 15.0574 11.349 15.0574 10.0006C15.0574 8.65228 14.5272 7.35802 13.5812 6.39719C12.6353 5.43636 11.3494 4.88603 10.0013 4.865V4.865ZM10.0013 6.66625C10.8854 6.66625 11.7334 7.01748 12.3586 7.64268C12.9838 8.26788 13.335 9.11583 13.335 10C13.335 10.8842 12.9838 11.7321 12.3586 12.3573C11.7334 12.9825 10.8854 13.3337 10.0013 13.3337C9.11709 13.3337 8.26913 12.9825 7.64393 12.3573C7.01873 11.7321 6.6675 10.8842 6.6675 10C6.6675 9.11583 7.01873 8.26788 7.64393 7.64268C8.26913 7.01748 9.11709 6.66625 10.0013 6.66625V6.66625Z"
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
                <p class="copyright">{{ 'landing.footer.copyright' | artemisTranslate: { year: currentYear } }}</p>
            </div>
        </footer>
    `,
})
export class LandingFooterComponent {
    protected readonly faLink: IconDefinition = faLink;

    private router: Router = inject(Router);
    linkGroups: FooterLinkGroup[] = FOOTER_LINK_GROUPS;
    currentYear: number = new Date().getFullYear();

    navigateToLogin(): void {
        this.router.navigateByUrl('/sign-in');
    }
}
