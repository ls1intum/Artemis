import { Component } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Accordion, AccordionContent, AccordionHeader, AccordionPanel } from 'primeng/accordion';
import { FAQ_ITEMS } from 'app/core/landing/landing-data';

@Component({
    selector: 'jhi-landing-faq',
    standalone: true,
    imports: [ArtemisTranslatePipe, Accordion, AccordionPanel, AccordionHeader, AccordionContent],
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
            display: flex;
            flex-direction: column;
            gap: 8px;
        }

        :host ::ng-deep .p-accordion {
            display: flex;
            flex-direction: column;
            gap: 8px;
        }

        :host ::ng-deep .p-accordionpanel {
            border: 0.75px solid var(--iris-accent-background);
            border-radius: 8px;
            background: var(--iris-secondary-background);
            overflow: clip;
        }

        :host ::ng-deep .p-accordionpanel-active {
            border-color: var(--primary);
            color: var(--body-color);
        }

        :host ::ng-deep .p-accordionheader {
            padding: 16px;
            font-size: 18px;
            font-weight: 500;
            color: var(--body-color);
            background: transparent;
            border: none;
        }

        :host ::ng-deep .p-accordioncontent-content {
            padding: 0 16px 16px;
            font-size: 16px;
            font-weight: 400;
            color: var(--text-body-secondary);
            line-height: 1.6;
            font-family: inherit;
        }

        :host ::ng-deep .p-collapsible-enter-active {
            animation-delay: 0.2s !important;
            animation-fill-mode: both !important;
        }

        :host ::ng-deep .p-accordioncontent .p-motion.p-collapsible-enter-active .p-accordioncontent-content {
            animation: faq-slide-down 220ms ease-out 0.2s both;
        }

        :host ::ng-deep .p-accordionpanel-active .p-accordionheader {
            color: var(--body-color) !important;
        }

        .faq-answer {
            margin: 0;
            font-family: inherit;
        }

        @keyframes faq-slide-down {
            from {
                opacity: 0;
            }
            to {
                opacity: 1;
            }
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

            :host ::ng-deep .p-accordionheader {
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
            <div class="faq-content">
                <p-accordion [multiple]="false">
                    @for (item of faqItems; track item.questionKey) {
                        <p-accordionpanel [value]="item.questionKey">
                            <p-accordionheader>{{ item.questionKey | artemisTranslate }}</p-accordionheader>
                            <p-accordioncontent>
                                <p class="faq-answer" [innerHTML]="formatAnswer(item.questionKey, item.answerKey | artemisTranslate)"></p>
                            </p-accordioncontent>
                        </p-accordionpanel>
                    }
                </p-accordion>
            </div>
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
            '<a class="text-primary fw-semibold" href="https://docs.artemis.tum.de" target="_blank" rel="noopener noreferrer">docs.artemis.tum.de</a>',
        );
    }
}
