import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowUpRightFromSquare } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { UNIVERSITY_LOGOS } from 'app/core/landing/landing-data';

@Component({
    selector: 'jhi-landing-social-proof',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, FaIconComponent],
    styles: `
        :host {
            display: block;
        }

        .social-proof {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 16px;
            padding: 80px 0;
        }

        .social-proof-title {
            font-size: 16px;
            font-weight: 500;
            color: var(--text-body-secondary);
            line-height: 1.6;
            text-align: center;
            margin: 0;
        }

        .social-proof-link {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            font-size: 14px;
            font-weight: 600;
            color: var(--primary-dark, var(--primary));
            text-decoration: none;
        }

        .social-proof-link:hover {
            color: var(--primary);
            text-decoration: underline;
        }

        .social-proof-link:focus-visible {
            outline: 2px solid var(--primary-dark, var(--primary));
            outline-offset: 2px;
        }

        .marquee-container {
            width: 100%;
            overflow: hidden;
            mask-image: linear-gradient(to right, transparent, black 10%, black 90%, transparent);
            -webkit-mask-image: linear-gradient(to right, transparent, black 10%, black 90%, transparent);
        }

        .marquee-track {
            display: flex;
            gap: 40px;
            align-items: center;
            animation: marquee 30s linear infinite;
            width: max-content;
        }

        .marquee-track:hover {
            animation-play-state: paused;
        }

        .logo-item {
            height: 36px;
            width: auto;
            flex-shrink: 0;
            opacity: 0.7;
            transition: opacity 0.2s;
            object-fit: contain;
        }

        .logo-item:hover {
            opacity: 1;
        }

        :host-context(html[prime-ng-use-dark-theme='false']) .logo-item.light-mode-black {
            filter: brightness(0) saturate(100%);
        }

        @keyframes marquee {
            0% {
                transform: translateX(0);
            }
            100% {
                transform: translateX(calc(-50% - 20px));
            }
        }

        @media (prefers-reduced-motion: reduce) {
            .marquee-track {
                animation: none;
            }
        }

        @media (max-width: 768px) {
            .social-proof {
                padding: 40px 0;
            }
        }
    `,
    template: `
        <section class="social-proof" [attr.aria-label]="'landing.socialProof.universitiesAria' | artemisTranslate">
            <h2 class="social-proof-title">{{ 'landing.socialProof.title' | artemisTranslate }}</h2>
            <div class="marquee-container">
                <div class="marquee-track">
                    @for (logo of logos; track logo.name) {
                        <img
                            class="logo-item"
                            [class.light-mode-black]="logo.isWhiteLogo"
                            [src]="logo.file"
                            [alt]="logo.name"
                            [style.width.px]="logo.width"
                            height="36"
                            [attr.width]="logo.width"
                            loading="lazy"
                            decoding="async"
                        />
                    }
                    @for (logo of logos; track logo.name) {
                        <img
                            class="logo-item"
                            [class.light-mode-black]="logo.isWhiteLogo"
                            [src]="logo.file"
                            [alt]=""
                            aria-hidden="true"
                            [style.width.px]="logo.width"
                            height="36"
                            [attr.width]="logo.width"
                            loading="lazy"
                            decoding="async"
                        />
                    }
                </div>
            </div>
            <a class="social-proof-link" href="https://docs.artemis.tum.de/about/adoption" target="_blank" rel="noopener">
                {{ 'landing.socialProof.cta' | artemisTranslate }}
                <fa-icon [icon]="faArrowUpRightFromSquare" />
                <span class="visually-hidden">{{ 'landing.opensInNewTab' | artemisTranslate }}</span>
            </a>
        </section>
    `,
})
export class LandingSocialProofComponent {
    protected readonly faArrowUpRightFromSquare = faArrowUpRightFromSquare;

    logos = UNIVERSITY_LOGOS;
}
