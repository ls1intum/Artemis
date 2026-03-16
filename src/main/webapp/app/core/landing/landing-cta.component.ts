import { Component } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { VisibleOnScrollDirective } from './visible-on-scroll.directive';

@Component({
    selector: 'jhi-landing-cta',
    standalone: true,
    imports: [TranslateDirective, VisibleOnScrollDirective],
    template: `
        <section class="cta-section">
            <div class="cta-container">
                <div class="cta-card" jhiVisibleOnScroll>
                    <h2 class="cta-title" jhiTranslate="landing.cta.title"></h2>
                    <p class="cta-subtitle" jhiTranslate="landing.cta.subtitle"></p>
                    <div class="cta-actions">
                        <a href="https://github.com/ls1intum/Artemis" target="_blank" rel="noopener noreferrer" class="cta-button">
                            <span jhiTranslate="landing.cta.button"></span>
                        </a>
                    </div>
                </div>
            </div>
        </section>
    `,
    styles: `
        .cta-container {
            max-width: 1280px;
            margin: 0 auto;
            padding: 0 1.25rem;
        }

        .cta-card {
            background: #0f172a;
            border-radius: 0.5rem;
            padding: 2rem;
            text-align: center;
            max-width: 64rem;
            margin: 5rem auto 0;
            display: flex;
            flex-direction: column;
            align-items: center;
            opacity: 0.5;
            transform: translateY(20px);
            transition: all 0.5s ease-in-out;
        }

        :host .cta-card.visible {
            opacity: 1;
            transform: translateY(0);
        }

        .cta-title {
            font-size: 2.25rem;
            line-height: 2.5rem;
            font-weight: 700;
            letter-spacing: -0.025em;
            background: radial-gradient(circle at top, rgb(255, 255, 255) 90%, rgb(255 255 255 / 0.38));
            box-decoration-break: clone;
            -webkit-background-clip: text;
            background-clip: text;
            -webkit-text-fill-color: transparent;
            padding-bottom: 4px;
        }

        .cta-subtitle {
            color: #e2e8f0;
            font-size: 1.125rem;
            line-height: 1.75rem;
            margin-top: 1rem;
        }

        .cta-actions {
            display: flex;
            margin-top: 1.25rem;
        }

        .cta-button {
            display: flex;
            align-items: center;
            justify-content: center;
            background: white;
            color: black;
            padding: 0.625rem 1.25rem;
            border-radius: 0.25rem;
            border: 2px solid transparent;
            font-weight: 400;
            text-decoration: none;
            text-align: center;
            box-shadow:
                0 10px 15px -3px rgb(0 0 0 / 0.1),
                0 4px 6px -4px rgb(0 0 0 / 0.1);
            transition:
                background 0.15s,
                color 0.15s;

            &:hover {
                background: #d1d5db;
            }
        }

        @media (min-width: 768px) {
            .cta-card {
                padding: 5rem;
            }

            .cta-title {
                font-size: 3.75rem;
                line-height: 1;
            }

            .cta-subtitle {
                font-size: 1.25rem;
                line-height: 1.75rem;
            }
        }

        @media (prefers-reduced-motion: reduce) {
            .cta-card {
                transition: none;
                opacity: 1;
                transform: none;
            }
        }
    `,
})
export class LandingCtaComponent {}
