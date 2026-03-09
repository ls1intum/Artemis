import { Component } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { VisibleOnScrollDirective } from './visible-on-scroll.directive';

interface CapabilityItem {
    id: string;
    icon: string;
    titleKey: string;
}

interface CapabilityGroup {
    id: string;
    items: CapabilityItem[];
}

const CAPABILITY_GROUPS: CapabilityGroup[] = [
    {
        id: 'ai',
        items: [
            {
                id: 'iris',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2a2 2 0 0 1 2 2c0 .74-.4 1.39-1 1.73V7h1a7 7 0 0 1 7 7h1a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1h-1.27A7 7 0 0 1 14 22h-4a7 7 0 0 1-6.73-3H2a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1h1a7 7 0 0 1 7-7h1V5.73A2 2 0 0 1 12 2z"/></svg>',
                titleKey: 'landing.narrative.sections.iris.title',
            },
            {
                id: 'atlas',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></svg>',
                titleKey: 'landing.narrative.sections.atlas.title',
            },
            {
                id: 'athena',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/></svg>',
                titleKey: 'landing.narrative.sections.athena.title',
            },
            {
                id: 'hyperion',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2v4"/><path d="M12 18v4"/><path d="M2 12h4"/><path d="M18 12h4"/><circle cx="12" cy="12" r="4"/></svg>',
                titleKey: 'landing.narrative.sections.hyperion.title',
            },
        ],
    },
    {
        id: 'assessment',
        items: [
            {
                id: 'programming',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>',
                titleKey: 'landing.narrative.sections.programming.title',
            },
            {
                id: 'quiz',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/></svg>',
                titleKey: 'landing.narrative.sections.quiz.title',
            },
            {
                id: 'modeling',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><path d="M14 17h7"/></svg>',
                titleKey: 'landing.narrative.sections.modeling.title',
            },
            {
                id: 'exams',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="17" rx="2"/><path d="M8 2v4"/><path d="M16 2v4"/><path d="M3 10h18"/></svg>',
                titleKey: 'landing.narrative.sections.exams.title',
            },
        ],
    },
    {
        id: 'teaching',
        items: [
            {
                id: 'lectures',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>',
                titleKey: 'landing.narrative.sections.lectures.title',
            },
            {
                id: 'communication',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>',
                titleKey: 'landing.narrative.sections.communication.title',
            },
            {
                id: 'tutorials',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/></svg>',
                titleKey: 'landing.narrative.summary.items.tutorials.title',
            },
        ],
    },
    {
        id: 'platform',
        items: [
            {
                id: 'integrity',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>',
                titleKey: 'landing.narrative.sections.integrity.title',
            },
            {
                id: 'opensource',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/></svg>',
                titleKey: 'landing.narrative.summary.items.opensource.title',
            },
            {
                id: 'scalability',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="2" width="20" height="8" rx="2"/><rect x="2" y="14" width="20" height="8" rx="2"/></svg>',
                titleKey: 'landing.narrative.summary.items.scalability.title',
            },
        ],
    },
];

@Component({
    selector: 'jhi-landing-capability-matrix',
    standalone: true,
    imports: [TranslateDirective, VisibleOnScrollDirective],
    template: `
        <section class="capability-matrix" aria-labelledby="capability-matrix-heading" jhiVisibleOnScroll>
            <div class="summary-header">
                <p class="summary-eyebrow" jhiTranslate="landing.narrative.summary.eyebrow"></p>
                <h3 id="capability-matrix-heading" class="summary-heading" jhiTranslate="landing.narrative.summary.heading"></h3>
                <p class="summary-description" jhiTranslate="landing.narrative.summary.description"></p>
            </div>

            <div class="summary-grid">
                @for (group of groups; track group.id) {
                    <article class="summary-group">
                        <p class="summary-group-label" [jhiTranslate]="'landing.narrative.summary.groups.' + group.id + '.title'"></p>
                        <p class="summary-group-description" [jhiTranslate]="'landing.narrative.summary.groups.' + group.id + '.description'"></p>
                        <div class="summary-items">
                            @for (item of group.items; track item.id) {
                                <div class="summary-item">
                                    <div class="summary-icon" [innerHTML]="item.icon" aria-hidden="true"></div>
                                    <span class="summary-item-label" [jhiTranslate]="item.titleKey"></span>
                                </div>
                            }
                        </div>
                    </article>
                }
            </div>
        </section>
    `,
    styles: `
        :host {
            display: block;
        }

        .capability-matrix {
            max-width: 1280px;
            margin: 0 auto;
            padding: 4.5rem 0 2rem;
            opacity: 0;
            transform: translateY(24px);
            transition:
                opacity 0.6s ease-out,
                transform 0.6s ease-out;
        }

        :host .capability-matrix.visible {
            opacity: 1;
            transform: translateY(0);
        }

        .summary-header {
            max-width: 780px;
            margin-bottom: 2.5rem;
        }

        .summary-eyebrow {
            margin: 0 0 0.75rem;
            color: #60a5fa;
            font-size: 0.75rem;
            font-weight: 700;
            letter-spacing: 0.16em;
            text-transform: uppercase;
        }

        .summary-heading {
            margin: 0;
            color: white;
            font-size: 2.4rem;
            line-height: 1.04;
            letter-spacing: -0.04em;
        }

        .summary-description {
            margin: 1rem 0 0;
            max-width: 46rem;
            color: #94a3b8;
            font-size: 1rem;
            line-height: 1.7;
        }

        .summary-grid {
            display: grid;
            grid-template-columns: repeat(2, minmax(0, 1fr));
            gap: 1rem;
        }

        .summary-group {
            background: rgba(10, 16, 28, 0.78);
            border: 1px solid rgba(30, 41, 59, 0.8);
            border-radius: 18px;
            padding: 1.5rem;
            box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
        }

        .summary-group-label {
            margin: 0;
            color: white;
            font-size: 1rem;
            font-weight: 700;
        }

        .summary-group-description {
            margin: 0.5rem 0 1.25rem;
            color: #64748b;
            font-size: 0.875rem;
            line-height: 1.6;
        }

        .summary-items {
            display: grid;
            grid-template-columns: repeat(2, minmax(0, 1fr));
            gap: 0.75rem;
        }

        .summary-item {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            padding: 0.7rem 0.8rem;
            border-radius: 12px;
            background: rgba(15, 23, 42, 0.56);
            border: 1px solid rgba(30, 41, 59, 0.6);
        }

        .summary-icon {
            flex-shrink: 0;
            color: #60a5fa;

            svg {
                width: 18px;
                height: 18px;
            }
        }

        .summary-item-label {
            color: #dbeafe;
            font-size: 0.875rem;
            line-height: 1.35;
        }

        @media (max-width: 1024px) {
            .summary-grid,
            .summary-items {
                grid-template-columns: 1fr;
            }

            .summary-heading {
                font-size: 2rem;
            }
        }

        @media (prefers-reduced-motion: reduce) {
            .capability-matrix {
                transition: none;
                opacity: 1;
                transform: none;
            }
        }
    `,
})
export class LandingCapabilityMatrixComponent {
    readonly groups = CAPABILITY_GROUPS;
}
