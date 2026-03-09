import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { VisibleOnScrollDirective } from './visible-on-scroll.directive';
import { ChapterId } from './landing-feature-data';

@Component({
    selector: 'jhi-landing-chapter-intro',
    standalone: true,
    imports: [TranslateDirective, VisibleOnScrollDirective],
    template: `
        <div class="section-header" jhiVisibleOnScroll>
            <h3 class="section-title" [jhiTranslate]="'landing.narrative.chapters.' + chapterId() + '.title'"></h3>
            <p class="section-subtitle" [jhiTranslate]="'landing.narrative.chapters.' + chapterId() + '.description'"></p>
        </div>
    `,
    styles: `
        :host {
            display: block;
        }

        .section-header {
            padding-top: 4rem;
            text-align: center;
            opacity: 0.5;
            transform: translateY(20px);
            transition: all 0.5s ease-in-out;
        }

        :host .section-header.visible {
            opacity: 1;
            transform: translateY(0);
        }

        .section-title {
            font-size: 2.25rem;
            line-height: 2.5rem;
            font-weight: 700;
            --hcol: rgb(255, 255, 255);
            --hcolt: rgb(255 255 255 / 0.38);
            background: radial-gradient(circle at top, var(--hcol) 30%, var(--hcolt));
            box-decoration-break: clone;
            -webkit-background-clip: text;
            background-clip: text;
            -webkit-text-fill-color: transparent;
            padding-bottom: 4px;
        }

        .section-subtitle {
            color: #cbd5e1;
            font-size: 1.125rem;
            line-height: 1.75rem;
            margin-top: 1rem;
        }

        @media (min-width: 1024px) {
            .section-title {
                font-size: 3rem;
                line-height: 1;
                letter-spacing: -0.025em;
            }
        }

        @media (prefers-reduced-motion: reduce) {
            .section-header {
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
