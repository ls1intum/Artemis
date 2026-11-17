import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FAQ_ITEMS } from 'app/core/landing/landing-data';

@Component({
    selector: 'jhi-landing-faq',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, FaIconComponent],
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

        .faq-panel {
            border: 0.75px solid var(--iris-accent-background);
            border-radius: 8px;
            background: var(--iris-secondary-background);
            overflow: clip;
        }

        .faq-panel.is-open {
            border-color: var(--primary);
        }

        .faq-question {
            margin: 0;
            font-size: inherit;
        }

        .faq-toggle {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 16px;
            width: 100%;
            padding: 16px;
            font-size: 18px;
            font-weight: 500;
            line-height: 1.5;
            text-align: start;
            color: var(--body-color);
            background: transparent;
            border: none;
            cursor: pointer;
        }

        .faq-toggle:focus-visible {
            outline: 2px solid var(--primary);
            outline-offset: -2px;
        }

        .faq-chevron {
            flex-shrink: 0;
            font-size: 14px;
            color: var(--text-body-secondary);
            transition: transform 0.2s ease;
        }

        .faq-panel.is-open .faq-chevron {
            transform: rotate(180deg);
        }

        .faq-region {
            display: grid;
            grid-template-rows: 0fr;
            visibility: hidden;
            transition:
                grid-template-rows 0.2s ease,
                visibility 0.2s;
        }

        .faq-panel.is-open .faq-region {
            grid-template-rows: 1fr;
            visibility: visible;
        }

        .faq-region-inner {
            min-height: 0;
            overflow: hidden;
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

        @media (prefers-reduced-motion: reduce) {
            .faq-region,
            .faq-chevron {
                transition: none;
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

            .faq-toggle {
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
                @for (item of faqItems; track item.questionKey; let i = $index) {
                    <div class="faq-panel" [class.is-open]="openItem() === item.questionKey">
                        <h3 class="faq-question">
                            <button
                                type="button"
                                class="faq-toggle"
                                [id]="'faq-toggle-' + i"
                                [attr.aria-expanded]="openItem() === item.questionKey"
                                [attr.aria-controls]="'faq-region-' + i"
                                (click)="toggle(item.questionKey)"
                            >
                                <span>{{ item.questionKey | artemisTranslate }}</span>
                                <fa-icon class="faq-chevron" [icon]="faChevronDown" />
                            </button>
                        </h3>
                        <div class="faq-region" role="region" [id]="'faq-region-' + i" [attr.aria-labelledby]="'faq-toggle-' + i">
                            <div class="faq-region-inner">
                                <p class="faq-answer" [innerHTML]="formatAnswer(item.questionKey, item.answerKey | artemisTranslate)"></p>
                            </div>
                        </div>
                    </div>
                }
            </div>
        </section>
    `,
})
export class LandingFaqComponent {
    protected readonly faChevronDown = faChevronDown;

    faqItems = FAQ_ITEMS;
    /** Question key of the open entry; only one entry is open at a time. */
    protected readonly openItem = signal<string | undefined>(undefined);

    protected toggle(questionKey: string): void {
        this.openItem.update((open) => (open === questionKey ? undefined : questionKey));
    }

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
