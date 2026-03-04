import { Component } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { VisibleOnScrollDirective } from './visible-on-scroll.directive';

@Component({
    selector: 'jhi-landing-features',
    standalone: true,
    imports: [TranslateDirective, VisibleOnScrollDirective],
    template: `
        <section class="features-section">
            <div class="section-header" jhiVisibleOnScroll>
                <h2 class="section-title" jhiTranslate="landing.features.title"></h2>
                <p class="section-subtitle" jhiTranslate="landing.features.subtitle"></p>
            </div>

            <div class="features-grid">
                @for (feature of features; track feature.index; let i = $index) {
                    <div class="feature-item" jhiVisibleOnScroll [style.transition-delay]="getDelay(i)">
                        <div class="feature-icon-wrapper">
                            <div class="feature-icon" [innerHTML]="feature.icon"></div>
                        </div>
                        <div>
                            <h3 class="feature-title" [jhiTranslate]="'landing.features.list.' + feature.index + '.title'"></h3>
                            <p class="feature-description" [jhiTranslate]="'landing.features.list.' + feature.index + '.description'"></p>
                        </div>
                    </div>
                }
            </div>
        </section>
    `,
    styles: `
        .features-section {
            max-width: 1280px;
            margin: 0 auto;
            padding: 0 1.25rem;
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
            color: white;
        }

        .section-subtitle {
            color: #cbd5e1;
            font-size: 1.125rem;
            line-height: 1.75rem;
            margin-top: 1rem;
        }

        .features-grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 4rem;
            margin-top: 4rem;
        }

        .feature-item {
            display: flex;
            gap: 1rem;
            align-items: flex-start;
            opacity: 0;
            transform: translateY(20px);
            transition: all 0.5s ease-in-out;
        }

        :host .feature-item.visible {
            opacity: 1;
            transform: translateY(0);
        }

        .feature-icon-wrapper {
            flex-shrink: 0;
            margin-top: 0.25rem;
            width: 2rem;
            height: 2rem;
        }

        .feature-icon {
            color: white;
            width: 32px;
            height: 32px;

            :host ::ng-deep svg {
                width: 32px;
                height: 32px;
            }
        }

        .feature-title {
            font-size: 1.125rem;
            line-height: 1.75rem;
            font-weight: 600;
            color: white;
        }

        .feature-description {
            color: #cbd5e1;
            line-height: 1.625;
            margin-top: 0.5rem;
        }

        @media (min-width: 640px) {
            .features-grid {
                grid-template-columns: repeat(2, 1fr);
            }
        }

        @media (min-width: 768px) {
            .features-grid {
                grid-template-columns: repeat(3, 1fr);
            }
        }

        @media (max-width: 639px) {
            .features-grid {
                grid-template-columns: 1fr;
            }
        }

        @media (min-width: 1024px) {
            .section-title {
                font-size: 3rem;
                line-height: 1;
                letter-spacing: -0.025em;
            }
        }
    `,
})
export class LandingFeaturesComponent {
    features = [
        {
            index: '0',
            icon: '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>',
        },
        {
            index: '1',
            icon: '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>',
        },
        {
            index: '2',
            icon: '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2a4 4 0 0 1 4 4c0 1.5-1 3-2 4l-2 2-2-2c-1-1-2-2.5-2-4a4 4 0 0 1 4-4z"/><path d="M12 16v6"/><path d="M8 22h8"/></svg>',
        },
        {
            index: '3',
            icon: '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="2" width="20" height="20" rx="2"/><path d="M7 7h10"/><path d="M7 12h10"/><path d="M7 17h6"/></svg>',
        },
        {
            index: '4',
            icon: '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></svg>',
        },
        {
            index: '5',
            icon: '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>',
        },
        {
            index: '6',
            icon: '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>',
        },
        {
            index: '7',
            icon: '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>',
        },
        {
            index: '8',
            icon: '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>',
        },
    ];

    getDelay(index: number): string {
        const row = Math.floor(index / 3);
        return `${row * 300}ms`;
    }
}
