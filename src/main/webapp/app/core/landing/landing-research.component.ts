import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowUpRightFromSquare } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { RESEARCH_STEPS } from 'app/core/landing/landing-data';

@Component({
    selector: 'jhi-landing-research',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, FaIconComponent],
    styles: `
        :host {
            display: block;
        }

        .research {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 40px;
            padding: 80px 160px;
            background: var(--iris-secondary-background);
        }

        .research-header {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 8px;
            max-width: 800px;
        }

        .research-label {
            font-size: 12px;
            font-weight: 400;
            color: var(--text-body-secondary);
            text-transform: uppercase;
            line-height: 1.6;
            letter-spacing: 0.05em;
        }

        .research-title {
            font-size: 40px;
            font-weight: 700;
            color: var(--body-color);
            line-height: 1.5;
            text-align: center;
            margin: 0;
        }

        .research-description {
            font-size: 16px;
            font-weight: 400;
            color: var(--text-body-secondary);
            line-height: 1.6;
            text-align: center;
            margin: 0;
        }

        .research-steps {
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 16px;
            width: 100%;
            margin: 0;
            padding: 0;
            list-style: none;
        }

        .research-step {
            display: flex;
            flex-direction: column;
            gap: 8px;
            padding: 24px;
            border-radius: 16px;
            border: 0.75px solid var(--iris-accent-background);
            background: var(--iris-primary-background);
        }

        .research-step-number {
            font-size: 12px;
            font-weight: 700;
            color: var(--primary-dark, var(--primary));
            text-transform: uppercase;
            letter-spacing: 0.08em;
        }

        .research-step-title {
            font-size: 18px;
            font-weight: 600;
            color: var(--body-color);
            line-height: 1.4;
            margin: 0;
        }

        .research-step-description {
            font-size: 15px;
            font-weight: 400;
            color: var(--text-body-secondary);
            line-height: 1.6;
            margin: 0;
        }

        .research-cta {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            font-size: 14px;
            font-weight: 600;
            color: var(--primary-dark, var(--primary));
            text-decoration: none;
        }

        .research-cta:hover {
            color: var(--primary);
            text-decoration: underline;
        }

        .research-cta:focus-visible {
            outline: 2px solid var(--primary-dark, var(--primary));
            outline-offset: 2px;
        }

        @media (max-width: 1200px) {
            .research {
                padding: 80px 40px;
            }

            .research-steps {
                grid-template-columns: repeat(2, 1fr);
            }
        }

        @media (max-width: 768px) {
            .research {
                padding: 40px 20px;
                gap: 24px;
            }

            .research-title {
                font-size: 32px;
            }

            .research-steps {
                grid-template-columns: 1fr;
            }
        }
    `,
    template: `
        <section class="research" id="research" aria-labelledby="research-title">
            <div class="research-header">
                <span class="research-label">{{ 'landing.research.label' | artemisTranslate }}</span>
                <h2 class="research-title" id="research-title">{{ 'landing.research.title' | artemisTranslate }}</h2>
                <p class="research-description">{{ 'landing.research.description' | artemisTranslate }}</p>
            </div>
            <ol class="research-steps">
                @for (step of steps; track step.titleKey; let index = $index) {
                    <li class="research-step">
                        <span class="research-step-number">{{ 'landing.research.stepLabel' | artemisTranslate: { n: index + 1 } }}</span>
                        <h3 class="research-step-title">{{ step.titleKey | artemisTranslate }}</h3>
                        <p class="research-step-description">{{ step.descriptionKey | artemisTranslate }}</p>
                    </li>
                }
            </ol>
            <a class="research-cta" href="https://docs.artemis.tum.de/publications" target="_blank" rel="noopener">
                {{ 'landing.research.cta' | artemisTranslate }}
                <fa-icon [icon]="faArrowUpRightFromSquare" />
                <span class="visually-hidden">{{ 'landing.opensInNewTab' | artemisTranslate }}</span>
            </a>
        </section>
    `,
})
export class LandingResearchComponent {
    protected readonly faArrowUpRightFromSquare = faArrowUpRightFromSquare;

    steps = RESEARCH_STEPS;
}
