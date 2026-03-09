import { Component } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { VisibleOnScrollDirective } from './visible-on-scroll.directive';

interface OtherFeature {
    id: string;
    icon: string;
}

const OTHER_FEATURES: OtherFeature[] = [
    {
        id: 'tutorials',
        icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
    },
    {
        id: 'opensource',
        icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>',
    },
    {
        id: 'scalability',
        icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="2" width="20" height="8" rx="2"/><rect x="2" y="14" width="20" height="8" rx="2"/><line x1="6" y1="6" x2="6.01" y2="6"/><line x1="6" y1="18" x2="6.01" y2="18"/></svg>',
    },
];

@Component({
    selector: 'jhi-landing-capability-matrix',
    standalone: true,
    imports: [TranslateDirective, VisibleOnScrollDirective],
    template: `
        <section class="other-features" aria-labelledby="other-features-heading" jhiVisibleOnScroll>
            <h3 id="other-features-heading" class="other-heading" jhiTranslate="landing.narrative.other.heading"></h3>
            <div class="other-list">
                @for (feature of features; track feature.id) {
                    <div class="other-item">
                        <div class="other-icon" [innerHTML]="feature.icon" aria-hidden="true"></div>
                        <div class="other-content">
                            <h4 [jhiTranslate]="'landing.narrative.other.' + feature.id + '.title'"></h4>
                            <p [jhiTranslate]="'landing.narrative.other.' + feature.id + '.description'"></p>
                        </div>
                    </div>
                }
            </div>
        </section>
    `,
    styles: `
        :host {
            display: block;
        }

        .other-features {
            padding: 4rem 0 2rem;
            max-width: 1280px;
            margin: 0 auto;
            opacity: 0;
            transform: translateY(24px);
            transition:
                opacity 0.6s ease-out,
                transform 0.6s ease-out;
        }

        :host .other-features.visible {
            opacity: 1;
            transform: translateY(0);
        }

        .other-heading {
            font-size: 1.5rem;
            font-weight: 700;
            color: #fff;
            margin: 0 0 2rem;
        }

        .other-list {
            display: flex;
            flex-direction: column;
            gap: 1rem;
        }

        .other-item {
            display: flex;
            align-items: flex-start;
            gap: 1.25rem;
            background: rgba(15, 23, 42, 0.5);
            border: 1px solid rgba(30, 41, 59, 0.6);
            border-radius: 12px;
            padding: 1.5rem;
            transition: border-color 0.2s ease;

            &:hover {
                border-color: #334155;
            }
        }

        .other-icon {
            flex-shrink: 0;
            color: #475569;
            margin-top: 0.125rem;

            svg {
                width: 24px;
                height: 24px;
            }
        }

        .other-content {
            min-width: 0;

            h4 {
                font-size: 1rem;
                font-weight: 600;
                color: #e2e8f0;
                margin: 0 0 0.25rem;
            }

            p {
                font-size: 0.875rem;
                color: #64748b;
                line-height: 1.6;
                margin: 0;
            }
        }

        @media (prefers-reduced-motion: reduce) {
            .other-features {
                transition: none;
                opacity: 1;
                transform: none;
            }
        }
    `,
})
export class LandingCapabilityMatrixComponent {
    readonly features = OTHER_FEATURES;
}
