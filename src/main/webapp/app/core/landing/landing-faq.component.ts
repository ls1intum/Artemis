import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TumAetUiAccordionComponent, TumAetUiAccordionPanelComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FAQ_ITEMS } from 'app/core/landing/landing-data';

@Component({
    selector: 'jhi-landing-faq',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, TumAetUiAccordionComponent, TumAetUiAccordionPanelComponent],
    styles: `
        :host {
            display: block;
        }

        .faq-section {
            display: flex;
            flex-direction: column;
            gap: 40px;
            align-items: center;
            padding: 80px 220px;
        }

        .faq-header {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 4px;
        }

        .faq-label {
            font-size: 12px;
            font-weight: 400;
            color: var(--text-body-secondary);
            text-transform: uppercase;
            line-height: 1.6;
            letter-spacing: 0.05em;
        }

        .faq-title {
            font-size: 48px;
            font-weight: 700;
            color: var(--body-color);
            line-height: 1.5;
            margin: 0;
        }

        .faq-content {
            width: 100%;
        }

        .faq-panel {
            --tumaet-ui-accordion-header-padding: 16px;
            --tumaet-ui-accordion-content-padding: 0;
            --tumaet-ui-accordion-icon-size: 14px;
            --tumaet-ui-accordion-icon-color: var(--text-body-secondary);

            border: 0.75px solid var(--iris-accent-background);
            border-radius: 8px;
            background: var(--iris-secondary-background);
            font-size: 18px;
            font-weight: 500;
            line-height: 1.5;
            color: var(--body-color);
        }

        .faq-panel[data-expanded='true'] {
            border-color: var(--primary);
        }

        .faq-answer {
            margin: 0;
            padding: 0 16px 16px;
            font-size: 16px;
            font-weight: 400;
            color: var(--text-body-secondary);
            line-height: 1.6;
            font-family: inherit;
        }

        @media (max-width: 1200px) {
            .faq-section {
                padding: 80px 40px;
            }
        }

        @media (max-width: 768px) {
            .faq-section {
                padding: 40px 20px;
            }

            .faq-title {
                font-size: 32px;
            }

            .faq-panel {
                font-size: 16px;
            }
        }
    `,
    template: `
        <section class="faq-section" id="faq">
            <div class="faq-header">
                <span class="faq-label">{{ 'landing.faq.label' | artemisTranslate }}</span>
                <h2 class="faq-title">{{ 'landing.faq.title' | artemisTranslate }}</h2>
            </div>
            <tumaet-ui-accordion class="faq-content">
                @for (item of faqItems; track item.questionKey) {
                    <tumaet-ui-accordion-panel class="faq-panel" [header]="item.questionKey | artemisTranslate">
                        <p class="faq-answer" [innerHTML]="formatAnswer(item.questionKey, item.answerKey | artemisTranslate)"></p>
                    </tumaet-ui-accordion-panel>
                }
            </tumaet-ui-accordion>
        </section>
    `,
})
export class LandingFaqComponent {
    faqItems = FAQ_ITEMS;

    /**
     * Wraps the docs URL in an anchor tag for the documentation FAQ answer.
     * Used with [innerHTML] — Angular's built-in sanitizer keeps safe tags like <a> intact.
     */
    formatAnswer(questionKey: string, translatedAnswer: string): string {
        if (questionKey !== 'landing.faq.items.docs.question') {
            return translatedAnswer;
        }
        return translatedAnswer.replace(
            /docs\.artemis(?:\.cit)?\.tum\.de/,
            '<a class="text-primary font-semibold" href="https://docs.artemis.tum.de" target="_blank" rel="noopener noreferrer">docs.artemis.tum.de</a>',
        );
    }
}
