import { Component, input } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { VisibleOnScrollDirective } from './visible-on-scroll.directive';
import { ChapterId } from './landing-feature-data';

@Component({
    selector: 'jhi-landing-chapter-intro',
    standalone: true,
    imports: [TranslateDirective, ArtemisTranslatePipe, VisibleOnScrollDirective],
    template: `
        <div class="chapter-intro" jhiVisibleOnScroll>
            <div class="chapter-kicker">
                <span class="chapter-eyebrow" [jhiTranslate]="'landing.narrative.chapters.' + chapterId() + '.eyebrow'"></span>
                <span class="chapter-rule" aria-hidden="true"></span>
            </div>
            <h3 class="chapter-title" [jhiTranslate]="'landing.narrative.chapters.' + chapterId() + '.title'"></h3>
            <p class="chapter-description" [innerHTML]="'landing.narrative.chapters.' + chapterId() + '.description' | artemisTranslate"></p>
        </div>
    `,
    styles: `
        :host {
            display: block;
        }

        .chapter-intro {
            position: relative;
            max-width: 760px;
            margin: 0 0 1.5rem;
            padding: 5rem 0 2rem;
            opacity: 0.5;
            transform: translateY(20px);
            transition:
                opacity 0.5s ease-in-out,
                transform 0.5s ease-in-out;
        }

        .chapter-intro::before {
            content: '';
            position: absolute;
            inset: 2.5rem -2rem 0;
            border-radius: 28px;
            background: radial-gradient(circle at top left, rgba(59, 130, 246, 0.14), transparent 48%), linear-gradient(180deg, rgba(15, 23, 42, 0.55), rgba(15, 23, 42, 0));
            pointer-events: none;
        }

        :host .chapter-intro.visible {
            opacity: 1;
            transform: translateY(0);
        }

        .chapter-kicker {
            display: flex;
            align-items: center;
            gap: 0.875rem;
            margin-bottom: 1rem;
        }

        .chapter-eyebrow {
            position: relative;
            z-index: 1;
            font-size: 0.75rem;
            font-weight: 700;
            letter-spacing: 0.16em;
            text-transform: uppercase;
            color: #60a5fa;
        }

        .chapter-rule {
            flex: 1;
            max-width: 180px;
            height: 1px;
            background: linear-gradient(90deg, rgba(96, 165, 250, 0.9), rgba(96, 165, 250, 0));
        }

        .chapter-title {
            position: relative;
            z-index: 1;
            font-size: 2.8rem;
            line-height: 1.02;
            font-weight: 750;
            letter-spacing: -0.04em;
            color: white;
            margin: 0;
            text-wrap: balance;
        }

        .chapter-description {
            position: relative;
            z-index: 1;
            max-width: 54rem;
            color: #cbd5e1;
            font-size: 1.125rem;
            line-height: 1.8;
            margin-top: 1rem;
        }

        @media (min-width: 1024px) {
            .chapter-title {
                font-size: 3.6rem;
            }
        }

        @media (prefers-reduced-motion: reduce) {
            .chapter-intro {
                transition: none;
                opacity: 1;
                transform: none;
            }
        }
    `,
})
export class LandingChapterIntroComponent {
    chapterId = input.required<ChapterId>();
}
