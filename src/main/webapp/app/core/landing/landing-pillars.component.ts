import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { PILLARS } from 'app/core/landing/landing-data';

@Component({
    selector: 'jhi-landing-pillars',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe],
    styles: `
        :host {
            display: block;
        }

        .pillars {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 40px;
            padding: 80px 160px;
        }

        .pillars-header {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 8px;
            max-width: 800px;
        }

        .pillars-label {
            font-size: 12px;
            font-weight: 400;
            color: var(--text-body-secondary);
            text-transform: uppercase;
            line-height: 1.6;
            letter-spacing: 0.05em;
        }

        .pillars-title {
            font-size: 40px;
            font-weight: 700;
            color: var(--body-color);
            line-height: 1.5;
            text-align: center;
            margin: 0;
        }

        .pillars-subtitle {
            font-size: 16px;
            font-weight: 400;
            color: var(--text-body-secondary);
            line-height: 1.6;
            text-align: center;
            margin: 0;
        }

        .pillars-grid {
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 16px;
            width: 100%;
            list-style: none;
            margin: 0;
            padding: 0;
        }

        .pillar {
            display: flex;
            flex-direction: column;
            gap: 8px;
            padding: 32px;
            border-radius: 16px;
            border: 0.75px solid var(--iris-accent-background);
            background: var(--iris-secondary-background);
        }

        .pillar-title {
            font-size: 20px;
            font-weight: 600;
            color: var(--body-color);
            line-height: 1.4;
            margin: 0;
        }

        .pillar-description {
            font-size: 15px;
            font-weight: 400;
            color: var(--text-body-secondary);
            line-height: 1.6;
            margin: 0;
        }

        @media (max-width: 1200px) {
            .pillars {
                padding: 80px 40px;
            }

            .pillars-grid {
                grid-template-columns: repeat(2, 1fr);
            }
        }

        @media (max-width: 768px) {
            .pillars {
                padding: 40px 20px;
                gap: 24px;
            }

            .pillars-title {
                font-size: 32px;
            }

            .pillars-grid {
                grid-template-columns: 1fr;
            }

            .pillar {
                padding: 24px;
            }
        }
    `,
    template: `
        <section class="pillars" id="pillars" aria-labelledby="pillars-title">
            <div class="pillars-header">
                <span class="pillars-label">{{ 'landing.pillars.label' | artemisTranslate }}</span>
                <h2 class="pillars-title" id="pillars-title">{{ 'landing.pillars.title' | artemisTranslate }}</h2>
                <p class="pillars-subtitle">{{ 'landing.pillars.subtitle' | artemisTranslate }}</p>
            </div>
            <ul class="pillars-grid">
                @for (pillar of pillars; track pillar.titleKey) {
                    <li class="pillar">
                        <h3 class="pillar-title">{{ pillar.titleKey | artemisTranslate }}</h3>
                        <p class="pillar-description">{{ pillar.descriptionKey | artemisTranslate }}</p>
                    </li>
                }
            </ul>
        </section>
    `,
})
export class LandingPillarsComponent {
    pillars = PILLARS;
}
