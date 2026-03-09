import { Component, ElementRef, Injector, OnDestroy, afterNextRender, effect, inject, signal, viewChildren } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { VisibleOnScrollDirective } from './visible-on-scroll.directive';

type ShowcaseFeatureId = 'iris' | 'atlas' | 'athena' | 'programming' | 'exams' | 'hyperion' | 'communication';

interface ShowcaseFeature {
    id: ShowcaseFeatureId;
    bullets: string[];
    visualType: 'image' | 'illustration';
    imageSrc?: string;
    imageAltKey?: string;
    illustrationIcon?: string;
}

interface Tier3Feature {
    id: string;
    icon: string;
}

interface LanguageIcon {
    name: string;
    href: string;
    sizeClass: string;
    icon: SafeHtml;
}

@Component({
    selector: 'jhi-landing-feature-showcase',
    standalone: true,
    imports: [TranslateDirective, ArtemisTranslatePipe, VisibleOnScrollDirective],
    template: `
        <section class="showcase" aria-labelledby="showcase-heading">
            <!-- Grid: rail + sections -->
            <div class="showcase-shell">
                <aside class="showcase-rail" [attr.aria-label]="'landing.showcase.heading' | artemisTranslate">
                    <nav class="showcase-nav">
                        @for (feature of features; track feature.id; let i = $index) {
                            <button
                                type="button"
                                class="showcase-nav-item"
                                [class.active]="activeIndex() === i"
                                [attr.aria-current]="activeIndex() === i ? 'true' : null"
                                (click)="scrollToFeature(i)"
                            >
                                <span class="nav-dot" aria-hidden="true"></span>
                                <span class="nav-label" [jhiTranslate]="'landing.showcase.' + feature.id + '.navLabel'"></span>
                            </button>
                        }
                    </nav>
                </aside>

                <div class="showcase-sections">
                    <h2 id="showcase-heading" class="visually-hidden" jhiTranslate="landing.showcase.heading"></h2>

                    @for (feature of features; track feature.id; let i = $index) {
                        <section
                            #sectionEl
                            class="showcase-section"
                            [id]="'feature-' + feature.id"
                            [attr.data-index]="i"
                            [class.reverse]="i % 2 === 1"
                            [class.expanded]="feature.id === 'programming' || feature.id === 'exams'"
                            [style.scroll-margin-top.rem]="6"
                            jhiVisibleOnScroll
                        >
                            <div class="showcase-text">
                                <span class="showcase-badge" [jhiTranslate]="'landing.showcase.' + feature.id + '.badge'"></span>
                                <h3 class="showcase-title" [jhiTranslate]="'landing.showcase.' + feature.id + '.title'"></h3>
                                <p class="showcase-description" [jhiTranslate]="'landing.showcase.' + feature.id + '.description'"></p>
                                @if (feature.bullets.length) {
                                    <ul class="showcase-bullets">
                                        @for (j of feature.bullets; track j) {
                                            <li [jhiTranslate]="'landing.showcase.' + feature.id + '.bullets.' + j"></li>
                                        }
                                    </ul>
                                }
                            </div>

                            <div class="showcase-visual">
                                @if (feature.visualType === 'image') {
                                    <img [src]="feature.imageSrc!" [alt]="feature.imageAltKey! | artemisTranslate" loading="lazy" />
                                } @else {
                                    <div class="illustration-card" [attr.data-feature]="feature.id" aria-hidden="true">
                                        <div class="illustration-glow"></div>
                                        <div class="illustration-icon" [innerHTML]="feature.illustrationIcon"></div>
                                    </div>
                                }
                            </div>

                            <!-- Programming Exercises: expanded sub-content -->
                            @if (feature.id === 'programming') {
                                <div class="expanded-content" jhiVisibleOnScroll>
                                    <div class="integration-cards">
                                        <div class="integration-card">
                                            <img src="content/images/landing/logo.png" alt="Artemis" class="card-logo" />
                                            <h4 jhiTranslate="landing.programmingExercises.integration.cards.git.title"></h4>
                                            <p jhiTranslate="landing.programmingExercises.integration.cards.git.description"></p>
                                        </div>
                                        <div class="integration-card">
                                            <img src="content/images/landing/logo.png" alt="Artemis" class="card-logo" />
                                            <h4 jhiTranslate="landing.programmingExercises.integration.cards.ci.title"></h4>
                                            <p jhiTranslate="landing.programmingExercises.integration.cards.ci.description"></p>
                                        </div>
                                        <div class="integration-card">
                                            <img src="content/images/landing/logo.png" alt="Artemis" class="card-logo" />
                                            <h4 jhiTranslate="landing.programmingExercises.integration.cards.batteries.title"></h4>
                                            <p jhiTranslate="landing.programmingExercises.integration.cards.batteries.description"></p>
                                        </div>
                                    </div>

                                    <div class="languages-row">
                                        <p class="languages-label" jhiTranslate="landing.programmingExercises.programmingLanguages"></p>
                                        <div class="languages-grid">
                                            @for (lang of languages; track lang.name) {
                                                <a
                                                    [href]="lang.href"
                                                    [attr.aria-label]="lang.name"
                                                    target="_blank"
                                                    rel="noopener"
                                                    [innerHTML]="lang.icon"
                                                    [class]="'language-icon ' + lang.sizeClass"
                                                ></a>
                                            }
                                        </div>
                                    </div>
                                </div>
                            }

                            <!-- Exams: expanded sub-content -->
                            @if (feature.id === 'exams') {
                                <div class="expanded-content" jhiVisibleOnScroll>
                                    <div class="exam-split">
                                        <div class="exam-split-image">
                                            <img src="content/images/landing/exam-stats.jpg" [alt]="'landing.examMode.statsImageAlt' | artemisTranslate" loading="lazy" />
                                        </div>
                                        <div class="exam-split-text">
                                            <h4 jhiTranslate="landing.examMode.statistics.title"></h4>
                                            <p jhiTranslate="landing.examMode.statistics.description"></p>
                                            <ul>
                                                <li jhiTranslate="landing.examMode.statistics.usingMetrics"></li>
                                                <li jhiTranslate="landing.examMode.statistics.studentFeedback"></li>
                                            </ul>
                                        </div>
                                    </div>
                                    <div class="exam-split reverse">
                                        <div class="exam-split-text">
                                            <h4 jhiTranslate="landing.examMode.automaticAssessment.title"></h4>
                                            <p jhiTranslate="landing.examMode.automaticAssessment.description"></p>
                                            <ul>
                                                <li jhiTranslate="landing.examMode.automaticAssessment.automate"></li>
                                                <li jhiTranslate="landing.examMode.automaticAssessment.quality"></li>
                                            </ul>
                                        </div>
                                        <div class="exam-split-image">
                                            <img src="content/images/landing/exam-assessment.png" [alt]="'landing.examMode.assessmentImageAlt' | artemisTranslate" loading="lazy" />
                                        </div>
                                    </div>
                                </div>
                            }
                        </section>
                    }
                </div>
            </div>

            <!-- Tier 3 grid: outside the grid so rail stops being sticky -->
            <section class="more-features" jhiVisibleOnScroll aria-labelledby="more-features-heading">
                <h3 id="more-features-heading" class="more-title" jhiTranslate="landing.showcase.more.title"></h3>
                <div class="more-grid">
                    @for (f of tier3Features; track f.id) {
                        <article class="more-item">
                            <div class="more-icon" [innerHTML]="f.icon" aria-hidden="true"></div>
                            <h4 [jhiTranslate]="'landing.showcase.more.' + f.id + '.title'"></h4>
                            <p [jhiTranslate]="'landing.showcase.more.' + f.id + '.description'"></p>
                        </article>
                    }
                </div>
            </section>
        </section>
    `,
    styles: `
        .showcase {
            position: relative;
            max-width: 1200px;
            margin: 0 auto;
            padding: 0 2rem;
        }

        .showcase-shell {
            display: grid;
            grid-template-columns: 180px minmax(0, 1fr);
            gap: 3rem;
            align-items: start;
        }

        .showcase-rail {
            position: sticky;
            top: max(6rem, 35vh);
            align-self: start;
            z-index: 10;
        }

        .showcase-nav {
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
        }

        .showcase-nav-item {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            background: none;
            border: 0;
            color: #475569;
            cursor: pointer;
            text-align: left;
            font-size: 0.8125rem;
            font-weight: 500;
            padding: 0.375rem 0;
            transition: color 0.3s ease;

            &.active {
                color: #fff;
            }

            &:hover:not(.active) {
                color: #94a3b8;
            }

            &:focus-visible {
                outline: 2px solid #fff;
                outline-offset: 4px;
                border-radius: 2px;
            }
        }

        .nav-dot {
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background: currentColor;
            flex-shrink: 0;
            transition:
                transform 0.3s ease,
                background 0.3s ease;
        }

        .showcase-nav-item.active .nav-dot {
            transform: scale(1.5);
            background: #3b82f6;
        }

        .showcase-sections {
            min-width: 0;
        }

        .showcase-section {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            gap: 3rem;
            padding: 5rem 0;
            border-bottom: 1px solid rgba(30, 41, 59, 0.5);
            opacity: 0;
            transform: translateY(24px);
            transition:
                opacity 0.6s ease-out,
                transform 0.6s ease-out;

            &:last-of-type {
                border-bottom: none;
            }

            &.reverse {
                flex-direction: row-reverse;
            }

            &.reverse .expanded-content {
                direction: ltr;
            }
        }

        :host .showcase-section.visible,
        :host .more-features.visible,
        :host .expanded-content.visible {
            opacity: 1;
            transform: translateY(0);
        }

        .showcase-text {
            flex: 1 1 0;
            min-width: 280px;
        }

        .showcase-visual {
            flex: 0 0 48%;
            min-width: 0;
            display: flex;
            align-items: center;
            justify-content: center;

            img {
                width: 100%;
                height: auto;
                border-radius: 12px;
                box-shadow:
                    0 25px 50px -12px rgba(0, 0, 0, 0.5),
                    0 0 0 1px rgba(255, 255, 255, 0.05);
            }
        }

        .showcase-badge {
            display: inline-block;
            font-size: 0.6875rem;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.12em;
            color: #60a5fa;
            margin-bottom: 0.75rem;
        }

        .showcase-title {
            font-size: 1.75rem;
            font-weight: 700;
            color: #fff;
            margin: 0 0 0.875rem;
            line-height: 1.25;
        }

        .showcase-description {
            font-size: 0.9375rem;
            line-height: 1.7;
            color: #94a3b8;
            margin: 0 0 1.25rem;
        }

        .showcase-bullets {
            list-style: none;
            padding: 0;
            margin: 0;
            display: flex;
            flex-direction: column;
            gap: 0.5rem;

            li {
                position: relative;
                padding-left: 1.125rem;
                font-size: 0.875rem;
                color: #94a3b8;
                line-height: 1.6;

                &::before {
                    content: '';
                    position: absolute;
                    left: 0;
                    top: 0.55em;
                    width: 5px;
                    height: 5px;
                    border-radius: 50%;
                    background: #3b82f6;
                }
            }
        }

        /* Illustration cards */
        .illustration-card {
            position: relative;
            width: 100%;
            aspect-ratio: 16 / 11;
            border-radius: 16px;
            background: #0c1425;
            border: 1px solid rgba(255, 255, 255, 0.06);
            display: flex;
            align-items: center;
            justify-content: center;
            overflow: hidden;
        }

        .illustration-glow {
            position: absolute;
            inset: -20%;
            opacity: 0.4;
            border-radius: inherit;
            filter: blur(40px);
        }

        .illustration-card[data-feature='iris'] .illustration-glow {
            background: radial-gradient(ellipse at 35% 45%, #3b82f6, transparent 65%);
        }

        .illustration-card[data-feature='atlas'] .illustration-glow {
            background: radial-gradient(ellipse at 55% 55%, #8b5cf6, transparent 65%);
        }

        .illustration-card[data-feature='athena'] .illustration-glow {
            background: radial-gradient(ellipse at 65% 40%, #06b6d4, transparent 65%);
        }

        .illustration-card[data-feature='hyperion'] .illustration-glow {
            background: radial-gradient(ellipse at 45% 50%, #f59e0b, transparent 65%);
        }

        .illustration-card[data-feature='communication'] .illustration-glow {
            background: radial-gradient(ellipse at 55% 45%, #10b981, transparent 65%);
        }

        .illustration-icon {
            position: relative;
            z-index: 1;
            color: rgba(255, 255, 255, 0.15);

            svg {
                width: 64px;
                height: 64px;
            }
        }

        /* Expanded sub-content (programming + exams) */
        .expanded-content {
            flex-basis: 100%;
            opacity: 0;
            transform: translateY(24px);
            transition:
                opacity 0.6s ease-out,
                transform 0.6s ease-out;
        }

        .integration-cards {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 1rem;
            margin-bottom: 2.5rem;
        }

        .integration-card {
            background: #0f172a;
            border: 1px solid #1e293b;
            border-radius: 10px;
            padding: 1.25rem;
            display: flex;
            flex-direction: column;
            gap: 0.5rem;

            .card-logo {
                height: 2rem;
                width: auto;
                align-self: flex-start;
            }

            h4 {
                font-size: 1rem;
                font-weight: 700;
                color: #fff;
                margin: 0;
            }

            p {
                font-size: 0.8125rem;
                color: #94a3b8;
                line-height: 1.6;
                margin: 0;
            }
        }

        .languages-row {
            text-align: center;
        }

        .languages-label {
            color: #94a3b8;
            font-size: 0.875rem;
            margin-bottom: 1.5rem;
        }

        .languages-grid {
            display: flex;
            flex-wrap: wrap;
            justify-content: center;
            align-items: center;
            gap: 3rem;
        }

        .language-icon {
            color: white;
            display: inline-flex;
            align-items: center;
            height: 2rem;
        }

        :host ::ng-deep .language-icon svg {
            height: 100%;
            width: auto;
            fill: currentColor;
        }

        .lang-md {
            height: 2.5rem;
        }

        .lang-lg {
            height: 3rem;
        }

        .lang-xl {
            height: 3.5rem;
        }

        /* Exam sub-content */
        .exam-split {
            display: flex;
            gap: 3rem;
            align-items: center;
            margin-bottom: 2.5rem;

            &.reverse {
                flex-direction: row-reverse;
            }
        }

        .exam-split-image {
            flex: 1;

            img {
                width: 100%;
                border-radius: 10px;
                box-shadow: 0 20px 40px -10px rgba(0, 0, 0, 0.4);
            }
        }

        .exam-split-text {
            flex: 1;

            h4 {
                font-size: 1.125rem;
                font-weight: 600;
                color: #fff;
                margin: 0 0 0.75rem;
            }

            p {
                color: #94a3b8;
                font-size: 0.875rem;
                line-height: 1.7;
                margin: 0 0 0.75rem;
            }

            ul {
                color: #94a3b8;
                font-size: 0.875rem;
                line-height: 1.7;
                padding-left: 1.25rem;
                margin: 0;

                li {
                    margin-bottom: 0.375rem;
                }
            }
        }

        /* Tier 3 grid */
        .more-features {
            padding: 5rem 0 3rem;
            opacity: 0;
            transform: translateY(24px);
            transition:
                opacity 0.6s ease-out,
                transform 0.6s ease-out;
        }

        .more-title {
            font-size: 1.25rem;
            font-weight: 700;
            color: #fff;
            margin: 0 0 2rem;
            text-align: center;
        }

        .more-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
            gap: 1rem;
        }

        .more-item {
            background: rgba(15, 23, 42, 0.6);
            border: 1px solid rgba(30, 41, 59, 0.6);
            border-radius: 10px;
            padding: 1.25rem;
            transition: border-color 0.2s ease;

            &:hover {
                border-color: #334155;
            }

            h4 {
                font-size: 0.9375rem;
                font-weight: 600;
                color: #e2e8f0;
                margin: 0.625rem 0 0.375rem;
            }

            p {
                font-size: 0.8125rem;
                color: #64748b;
                line-height: 1.6;
                margin: 0;
            }
        }

        .more-icon {
            color: #475569;

            svg {
                width: 24px;
                height: 24px;
            }
        }

        @media (max-width: 1024px) {
            .showcase-shell {
                grid-template-columns: 1fr;
            }

            .showcase-rail {
                display: none;
            }

            .showcase-section,
            .showcase-section.reverse {
                flex-direction: column;
                gap: 2rem;
                padding: 3rem 0;
            }

            .showcase-visual {
                flex: 0 0 auto;
            }

            .showcase-title {
                font-size: 1.5rem;
            }

            .integration-cards {
                grid-template-columns: 1fr;
            }

            .exam-split,
            .exam-split.reverse {
                flex-direction: column;
                gap: 2rem;
            }
        }

        @media (prefers-reduced-motion: reduce) {
            .showcase-section,
            .more-features,
            .expanded-content,
            .showcase-nav-item,
            .nav-dot {
                transition: none;
            }

            .showcase-section,
            .more-features,
            .expanded-content {
                opacity: 1;
                transform: none;
            }
        }
    `,
})
export class LandingFeatureShowcaseComponent implements OnDestroy {
    private readonly host = inject(ElementRef<HTMLElement>);
    private readonly injector = inject(Injector);
    private readonly sanitizer = inject(DomSanitizer);
    readonly sectionElements = viewChildren<ElementRef<HTMLElement>>('sectionEl');
    readonly activeIndex = signal(0);

    private observer?: IntersectionObserver;
    private readonly visibilityRatios = new Map<HTMLElement, number>();

    readonly features: ShowcaseFeature[] = [
        {
            id: 'iris',
            bullets: ['0', '1', '2'],
            visualType: 'illustration',
            illustrationIcon: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2a2 2 0 0 1 2 2c0 .74-.4 1.39-1 1.73V7h1a7 7 0 0 1 7 7h1a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1h-1.27A7 7 0 0 1 14 22h-4a7 7 0 0 1-6.73-3H2a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1h1a7 7 0 0 1 7-7h1V5.73A2 2 0 0 1 12 2z"/><circle cx="10" cy="15" r="1"/><circle cx="14" cy="15" r="1"/></svg>`,
        },
        {
            id: 'atlas',
            bullets: ['0', '1', '2'],
            visualType: 'illustration',
            illustrationIcon: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/><path d="M6 8h2"/><path d="M6 12h2"/><path d="M16 8h2"/><path d="M16 12h2"/></svg>`,
        },
        {
            id: 'athena',
            bullets: ['0', '1', '2'],
            visualType: 'illustration',
            illustrationIcon: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/><path d="M9 15l2 2 4-4"/></svg>`,
        },
        {
            id: 'programming',
            bullets: ['0', '1', '2'],
            visualType: 'image',
            imageSrc: 'content/images/landing/editor.png',
            imageAltKey: 'landing.showcase.programming.imageAlt',
        },
        {
            id: 'exams',
            bullets: ['0', '1', '2'],
            visualType: 'image',
            imageSrc: 'content/images/landing/exam-participate.png',
            imageAltKey: 'landing.showcase.exams.imageAlt',
        },
        {
            id: 'hyperion',
            bullets: ['0', '1', '2'],
            visualType: 'illustration',
            illustrationIcon: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2v4"/><path d="M12 18v4"/><path d="m4.93 4.93 2.83 2.83"/><path d="m16.24 16.24 2.83 2.83"/><path d="M2 12h4"/><path d="M18 12h4"/><path d="m4.93 19.07 2.83-2.83"/><path d="m16.24 4.76 2.83-2.83"/><circle cx="12" cy="12" r="4"/></svg>`,
        },
        {
            id: 'communication',
            bullets: ['0', '1', '2'],
            visualType: 'illustration',
            illustrationIcon: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/><path d="M8 10h.01"/><path d="M12 10h.01"/><path d="M16 10h.01"/></svg>`,
        },
    ];

    readonly tier3Features: Tier3Feature[] = [
        {
            id: 'plagiarism',
            icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>',
        },
        {
            id: 'modeling',
            icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>',
        },
        {
            id: 'quiz',
            icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><path d="M12 17h.01"/></svg>',
        },
        {
            id: 'lectures',
            icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>',
        },
        {
            id: 'analytics',
            icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>',
        },
        {
            id: 'tutorials',
            icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
        },
        {
            id: 'opensource',
            icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>',
        },
    ];

    // Static language icon data — trusted static SVG content
    readonly languages: LanguageIcon[] = this.buildLanguages();

    private readonly reducedMotion = typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    constructor() {
        effect((onCleanup) => {
            const sections = this.sectionElements();
            if (!sections.length) {
                this.observer?.disconnect();
                this.visibilityRatios.clear();
                return;
            }

            let cancelled = false;
            onCleanup(() => {
                cancelled = true;
                this.observer?.disconnect();
                this.visibilityRatios.clear();
            });

            afterNextRender(
                () => {
                    if (cancelled) return;

                    const root = this.host.nativeElement.closest('.page-wrapper');
                    if (!(root instanceof HTMLElement)) {
                        return;
                    }

                    this.observer?.disconnect();
                    this.visibilityRatios.clear();

                    this.observer = new IntersectionObserver(
                        (entries) => {
                            for (const entry of entries) {
                                this.visibilityRatios.set(entry.target as HTMLElement, entry.intersectionRatio);
                            }

                            let maxRatio = -1;
                            let maxIndex = this.activeIndex();
                            for (const section of sections) {
                                const element = section.nativeElement;
                                const ratio = this.visibilityRatios.get(element) ?? 0;
                                const index = Number(element.dataset['index'] ?? -1);
                                if (ratio > maxRatio && index >= 0) {
                                    maxRatio = ratio;
                                    maxIndex = index;
                                }
                            }

                            if (maxRatio > 0) {
                                this.activeIndex.set(maxIndex);
                            }
                        },
                        {
                            root,
                            rootMargin: '-96px 0px -35% 0px',
                            threshold: [0, 0.15, 0.3, 0.5, 0.75, 1],
                        },
                    );

                    for (const section of sections) {
                        this.observer.observe(section.nativeElement);
                    }
                },
                { injector: this.injector },
            );
        });
    }

    scrollToFeature(index: number): void {
        const section = this.sectionElements()[index]?.nativeElement;
        const container = this.host.nativeElement.closest('.page-wrapper');
        if (!(section instanceof HTMLElement) || !(container instanceof HTMLElement)) {
            return;
        }

        const topOffset = 96;
        const top = section.getBoundingClientRect().top + container.scrollTop - topOffset;
        container.scrollTo({ top, behavior: this.reducedMotion ? 'instant' : 'smooth' });
    }

    ngOnDestroy(): void {
        this.observer?.disconnect();
        this.visibilityRatios.clear();
    }

    private buildLanguages(): LanguageIcon[] {
        const raw = [
            {
                name: 'Java',
                href: 'https://www.oracle.com/java/',
                sizeClass: 'lang-md',
                svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M8.851 18.56s-.917.534.653.714c1.902.218 2.874.187 4.969-.211 0 0 .552.346 1.321.646-4.699 2.013-10.633-.118-6.943-1.149M8.276 15.933s-1.028.761.542.924c2.032.209 3.636.227 6.413-.308 0 0 .384.389.987.602-5.679 1.661-12.007.13-7.942-1.218M13.116 11.475c1.158 1.333-.304 2.533-.304 2.533s2.939-1.518 1.589-3.418c-1.261-1.772-2.228-2.652 3.007-5.688 0-.001-8.216 2.051-4.292 6.573M19.33 20.504s.679.559-.747.991c-2.712.822-11.288 1.069-13.669.033-.856-.373.75-.89 1.254-.998.527-.114.828-.093.828-.093-.953-.671-6.156 1.317-2.643 1.887 9.58 1.553 17.462-.7 14.977-1.82M9.292 13.21s-4.362 1.036-1.544 1.412c1.189.159 3.561.123 5.77-.062 1.806-.152 3.618-.477 3.618-.477s-.637.272-1.098.587c-4.429 1.165-12.986.623-10.522-.568 2.082-1.006 3.776-.892 3.776-.892M17.116 17.584c4.503-2.34 2.421-4.589.968-4.285-.355.074-.515.138-.515.138s.132-.207.385-.297c2.875-1.011 5.086 2.981-.928 4.562 0-.001.07-.062.09-.118M14.401 0s2.494 2.494-2.365 6.33c-3.896 3.077-.888 4.832-.001 6.836-2.274-2.053-3.943-3.858-2.824-5.539 1.644-2.469 6.197-3.665 5.19-7.627M9.734 23.924c4.322.277 10.959-.153 11.116-2.198 0 0-.302.775-3.572 1.391-3.688.694-8.239.613-10.937.168 0-.001.553.457 3.393.639"/></svg>',
            },
            {
                name: 'Swift',
                href: 'https://developer.apple.com/swift/',
                sizeClass: 'lang-md',
                svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="m7.508 0l-.86.002q-.362.001-.724.01q-.198.005-.395.015A9 9 0 0 0 4.348.15 5.5 5.5 0 0 0 2.85.645 5.04 5.04 0 0 0 .645 2.848c-.245.48-.4.972-.495 1.5c-.093.52-.122 1.05-.136 1.576a35 35 0 0 0-.012.724L0 7.508v8.984l.002.862q.002.36.012.722c.014.526.043 1.057.136 1.576c.095.528.25 1.02.495 1.5a5.03 5.03 0 0 0 2.205 2.203c.48.244.97.4 1.498.495c.52.093 1.05.124 1.576.138q.362.01.724.01l.86.002h8.984l.86-.002q.362 0 .724-.01a10.5 10.5 0 0 0 1.578-.138 5.3 5.3 0 0 0 1.498-.495 5.04 5.04 0 0 0 2.203-2.203c.245-.48.4-.972.495-1.5c.093-.52.124-1.05.138-1.576q.01-.361.01-.722l.002-.862V7.508l-.002-.86a34 34 0 0 0-.01-.724 10.5 10.5 0 0 0-.138-1.576 5.3 5.3 0 0 0-.495-1.5A5.04 5.04 0 0 0 21.152.645 5.3 5.3 0 0 0 19.654.15a10.5 10.5 0 0 0-1.578-.138 35 35 0 0 0-.722-.01L16.492 0zm6.035 3.41c4.114 2.47 6.545 7.162 5.549 11.131c-.024.093-.05.181-.076.272l.002.001c2.062 2.538 1.5 5.258 1.236 4.745c-1.072-2.086-3.066-1.568-4.088-1.043a7 7 0 0 1-.281.158l-.02.012-.002.002c-2.115 1.123-4.957 1.205-7.812-.022a12.57 12.57 0 0 1-5.64-4.838c.649.48 1.35.902 2.097 1.252c3.019 1.414 6.051 1.311 8.197-.002C9.651 12.73 7.101 9.67 5.146 7.191a10.6 10.6 0 0 1-1.005-1.384c2.34 2.142 6.038 4.83 7.365 5.576C8.69 8.408 6.208 4.743 6.324 4.86c4.436 4.47 8.528 6.996 8.528 6.996c.154.085.27.154.36.213q.128-.322.224-.668c.708-2.588-.09-5.548-1.893-7.992z"/></svg>',
            },
            {
                name: 'Kotlin',
                href: 'https://kotlinlang.org/',
                sizeClass: 'lang-md',
                svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M24 24H0V0h24L12 12Z"/></svg>',
            },
            {
                name: 'Python',
                href: 'https://www.python.org/',
                sizeClass: 'lang-md',
                svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="m14.25.18l.9.2l.73.26l.59.3l.45.32l.34.34l.25.34l.16.33l.1.3l.04.26l.02.2l-.01.13V8.5l-.05.63l-.13.55l-.21.46l-.26.38l-.3.31l-.33.25l-.35.19l-.35.14l-.33.1l-.3.07l-.26.04l-.21.02H8.77l-.69.05l-.59.14l-.5.22l-.41.27l-.33.32l-.27.35l-.2.36l-.15.37l-.1.35l-.07.32l-.04.27l-.02.21v3.06H3.17l-.21-.03l-.28-.07l-.32-.12l-.35-.18l-.36-.26l-.36-.36l-.35-.46l-.32-.59l-.28-.73l-.21-.88l-.14-1.05l-.05-1.23l.06-1.22l.16-1.04l.24-.87l.32-.71l.36-.57l.4-.44l.42-.33l.42-.24l.4-.16l.36-.1l.32-.05l.24-.01h.16l.06.01h8.16v-.83H6.18l-.01-2.75l-.02-.37l.05-.34l.11-.31l.17-.28l.25-.26l.31-.23l.38-.2l.44-.18l.51-.15l.58-.12l.64-.1l.71-.06l.77-.04l.84-.02l1.27.05zm-6.3 1.98l-.23.33l-.08.41l.08.41l.23.34l.33.22l.41.09l.41-.09l.33-.22l.23-.34l.08-.41l-.08-.41l-.23-.33l-.33-.22l-.41-.09l-.41.09zm13.09 3.95l.28.06l.32.12l.35.18l.36.27l.36.35l.35.47l.32.59l.28.73l.21.88l.14 1.04l.05 1.23l-.06 1.23l-.16 1.04l-.24.86l-.32.71l-.36.57l-.4.45l-.42.33l-.42.24l-.4.16l-.36.09l-.32.05l-.24.02l-.16-.01h-8.22v.82h5.84l.01 2.76l.02.36l-.05.34l-.11.31l-.17.29l-.25.25l-.31.24l-.38.2l-.44.17l-.51.15l-.58.13l-.64.09l-.71.07l-.77.04l-.84.01l-1.27-.04l-1.07-.14l-.9-.2l-.73-.25l-.59-.3l-.45-.33l-.34-.34l-.25-.34l-.16-.33l-.1-.3l-.04-.25l-.02-.2l.01-.13v-5.34l.05-.64l.13-.54l.21-.46l.26-.38l.3-.32l.33-.24l.35-.2l.35-.14l.33-.1l.3-.06l.26-.04l.21-.02l.13-.01h5.84l.69-.05l.59-.14l.5-.21l.41-.28l.33-.32l.27-.35l.2-.36l.15-.36l.1-.35l.07-.32l.04-.28l.02-.21V6.07h2.09l.14.01zm-6.47 14.25l-.23.33l-.08.41l.08.41l.23.33l.33.23l.41.08l.41-.08l.33-.23l.23-.33l.08-.41l-.08-.41l-.23-.33l-.33-.23l-.41-.08l-.41.08z"/></svg>',
            },
            {
                name: 'C',
                href: 'https://www.iso.org/standard/74528.html',
                sizeClass: 'lang-lg',
                svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M16.592 9.196s-.354-3.298-3.627-3.39c-3.274-.09-4.955 2.474-4.955 6.14s1.858 6.597 5.045 6.597c3.184 0 3.538-3.665 3.538-3.665l6.104.365s.36 3.31-2.196 5.836c-2.552 2.524-5.69 2.937-7.876 2.92c-2.19-.016-5.226.035-8.16-2.97c-2.938-3.01-3.436-5.93-3.436-8.8s.556-6.67 4.047-9.55C7.444.72 9.849 0 12.254 0c10.042 0 10.717 9.26 10.717 9.26z"/></svg>',
            },
            {
                name: 'OCaml',
                href: 'https://ocaml.org/',
                sizeClass: 'lang-xl',
                svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M12.178 21.637c-.085-.17-.187-.524-.255-.676c-.067-.135-.27-.506-.37-.625c-.22-.253-.27-.27-.338-.608c-.12-.574-.405-1.588-.76-2.296c-.187-.372-.49-.677-.761-.947c-.236-.236-.777-.624-.878-.607c-.895.169-1.166 1.046-1.587 1.739c-.237.388-.473.71-.66 1.115c-.167.371-.151.793-.439 1.115a2.95 2.95 0 0 0-.624 1.097c-.034.084-.101.929-.186 1.131l1.318-.084c1.233.085.877.557 2.787.456l3.022-.1a5.4 5.4 0 0 0-.27-.71zM20.96 1.539H3.023A3.02 3.02 0 0 0 0 4.56v6.587c.44-.152 1.047-1.08 1.25-1.3c.337-.389.405-.895.574-1.2c.389-.709.456-1.215 1.334-1.215c.406 0 .575.1.845.473c.186.253.523.743.675 1.064c.186.371.474.86.609.962c.1.068.185.136.27.17c.135.05.253-.051.354-.12c.118-.1.17-.286.287-.556c.17-.39.339-.827.44-.997c.169-.27.236-.608.422-.76c.27-.236.641-.253.743-.27c.557-.118.81.27 1.08.507c.186.168.423.49.609.91c.135.339.304.661.388.846c.068.185.237.49.338.86c.101.322.337.575.44.744c0 0 .152.406 1.03.778a8 8 0 0 0 .81.286c.39.135.76.12 1.233.068c.338 0 .524-.49.676-.878c.084-.237.185-.895.236-1.081s-.085-.32.034-.49c.135-.186.22-.203.287-.439c.17-.523 1.114-.54 1.655-.54c.456 0 .389.44 1.149.287c.439-.085.86.05 1.318.185c.388.102.76.22.98.473c.134.17.489.997.134 1.031c.033.033.067.118.118.151c-.085.322-.422.085-.625.051c-.253-.05-.44 0-.693.118c-.439.187-1.063.17-1.452.49c-.32.271-.32.861-.473 1.2c0 0-.422 1.063-1.317 1.722c-.237.17-.692.574-1.672.726c-.44.068-.86.068-1.318.05c-.22-.016-.438-.016-.658-.016c-.136 0-.575-.017-.558.034l-.05.119a.6.6 0 0 0 .033.169c.017.1.017.185.034.27c0 .185-.017.388 0 .574c.017.388.17.743.186 1.148c.017.44.236.913.456 1.267c.085.135.203.152.254.32c.067.186 0 .406.033.609c.118.794.355 1.638.71 2.364v.017c.439-.067.895-.236 1.47-.32c1.063-.153 2.532-.085 3.478-.17c2.399-.22 3.7.98 5.844.49V4.562a3.045 3.045 0 0 0-3.04-3.023m-8.951 14.187q0-.051 0 0m-6.47 2.769c.17-.372.271-.778.406-1.15c.135-.354.337-.86.693-1.046c-.05-.05-.744-.068-.929-.085a7 7 0 0 1-.608-.084a23 23 0 0 1-1.15-.236c-.22-.051-.979-.322-1.13-.39c-.39-.168-.642-.658-.93-.607c-.185.034-.37.101-.49.287c-.1.152-.134.423-.202.608c-.084.203-.22.405-.32.608c-.238.354-.626.676-.795 1.03c-.033.085-.05.169-.084.254v4.07c.202.034.405.068.624.135c1.69.456 2.095.49 3.75.304l.152-.017c.118-.27.22-1.165.304-1.435c.067-.22.153-.39.187-.591c.033-.203 0-.406-.017-.59c-.034-.491.354-.661.54-1.065z"/></svg>',
            },
            {
                name: 'Haskell',
                href: 'https://www.haskell.org/',
                sizeClass: 'lang-xl',
                svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M0 3.535L5.647 12 0 20.465h4.235L9.883 12 4.235 3.535zm5.647 0L11.294 12l-5.647 8.465h4.235l3.53-5.29 3.53 5.29h4.234L9.883 3.535zm8.941 4.938l1.883 2.822H24V8.473zm2.824 4.232l1.882 2.822H24v-2.822z"/></svg>',
            },
        ];
        return raw.map((lang) => ({
            name: lang.name,
            href: lang.href,
            sizeClass: lang.sizeClass,
            icon: this.sanitizer.bypassSecurityTrustHtml(lang.svg),
        }));
    }
}
