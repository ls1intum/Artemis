import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { UNIVERSITY_LOGOS } from 'app/core/landing/landing-data';

@Component({
    selector: 'jhi-landing-social-proof',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe],
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
            will-change: transform;
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
        <section class="social-proof">
            <p class="social-proof-title">{{ 'landing.socialProof.title' | artemisTranslate }}</p>
            <div class="marquee-container">
                <div class="marquee-track" aria-label="Universities using Artemis">
                    @for (logo of logos; track logo.name; let idx = $index) {
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
        </section>
    `,
})
export class LandingSocialProofComponent {
    logos = UNIVERSITY_LOGOS;
}
