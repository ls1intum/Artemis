import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowUpRightFromSquare } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TRUST_LINKS } from 'app/core/landing/landing-data';

@Component({
    selector: 'jhi-landing-trust',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, FaIconComponent],
    styles: `
        :host {
            display: block;
        }

        .trust {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 40px;
            padding: 80px 160px;
        }

        .trust-header {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 8px;
            max-width: 800px;
        }

        .trust-label {
            font-size: 12px;
            font-weight: 400;
            color: var(--text-body-secondary);
            text-transform: uppercase;
            line-height: 1.6;
            letter-spacing: 0.05em;
        }

        .trust-title {
            font-size: 40px;
            font-weight: 700;
            color: var(--body-color);
            line-height: 1.5;
            text-align: center;
            margin: 0;
        }

        .trust-subtitle {
            font-size: 16px;
            font-weight: 400;
            color: var(--text-body-secondary);
            line-height: 1.6;
            text-align: center;
            margin: 0;
        }

        .trust-grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 16px;
            width: 100%;
            list-style: none;
            margin: 0;
            padding: 0;
        }

        .trust-card {
            display: flex;
            flex-direction: column;
            gap: 8px;
            height: 100%;
            padding: 24px;
            border-radius: 16px;
            border: 0.75px solid var(--iris-accent-background);
            background: var(--iris-secondary-background);
            text-decoration: none;
            transition: border-color 0.2s;
        }

        .trust-card:hover {
            border-color: var(--primary);
            text-decoration: none;
        }

        .trust-card:focus-visible {
            outline: 2px solid var(--primary-dark, var(--primary));
            outline-offset: 2px;
        }

        .trust-card-title {
            display: flex;
            align-items: center;
            gap: 8px;
            font-size: 18px;
            font-weight: 600;
            color: var(--body-color);
            line-height: 1.4;
            margin: 0;
        }

        .trust-card-icon {
            font-size: 12px;
            color: var(--text-body-secondary);
        }

        .trust-card-description {
            font-size: 15px;
            font-weight: 400;
            color: var(--text-body-secondary);
            line-height: 1.6;
            margin: 0;
        }

        .trust-note {
            font-size: 14px;
            font-weight: 400;
            color: var(--text-body-secondary);
            line-height: 1.6;
            text-align: center;
            max-width: 800px;
            margin: 0;
        }

        @media (max-width: 1200px) {
            .trust {
                padding: 80px 40px;
            }

            .trust-grid {
                grid-template-columns: repeat(2, 1fr);
            }
        }

        @media (max-width: 768px) {
            .trust {
                padding: 40px 20px;
                gap: 24px;
            }

            .trust-title {
                font-size: 32px;
            }

            .trust-grid {
                grid-template-columns: 1fr;
            }
        }

        @media (prefers-reduced-motion: reduce) {
            .trust-card {
                transition: none;
            }
        }
    `,
    template: `
        <section class="trust" id="trust" aria-labelledby="trust-title">
            <div class="trust-header">
                <span class="trust-label">{{ 'landing.trust.label' | artemisTranslate }}</span>
                <h2 class="trust-title" id="trust-title">{{ 'landing.trust.title' | artemisTranslate }}</h2>
                <p class="trust-subtitle">{{ 'landing.trust.subtitle' | artemisTranslate }}</p>
            </div>
            <ul class="trust-grid">
                @for (link of links; track link.href) {
                    <li>
                        <a class="trust-card" [href]="link.href" target="_blank" rel="noopener">
                            <h3 class="trust-card-title">
                                {{ link.titleKey | artemisTranslate }}
                                <fa-icon class="trust-card-icon" [icon]="faArrowUpRightFromSquare" />
                                <span class="visually-hidden">{{ 'landing.opensInNewTab' | artemisTranslate }}</span>
                            </h3>
                            <p class="trust-card-description">{{ link.descriptionKey | artemisTranslate }}</p>
                        </a>
                    </li>
                }
            </ul>
            <p class="trust-note">{{ 'landing.trust.note' | artemisTranslate }}</p>
        </section>
    `,
})
export class LandingTrustComponent {
    protected readonly faArrowUpRightFromSquare = faArrowUpRightFromSquare;

    links = TRUST_LINKS;
}
