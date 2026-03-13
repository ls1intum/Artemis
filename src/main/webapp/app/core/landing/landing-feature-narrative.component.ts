import { Component, ElementRef, Injector, OnDestroy, afterNextRender, effect, inject, signal, viewChildren } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LandingChapterIntroComponent } from './landing-chapter-intro.component';
import { LandingFeatureSectionComponent } from './landing-feature-section.component';
import { LANDING_CHAPTERS, LANDING_SECTIONS, LandingFeatureSection } from './landing-feature-data';
import { LandingGlowbarComponent } from './landing-glowbar.component';
import { LandingSupportStripComponent } from './landing-support-strip.component';

@Component({
    selector: 'jhi-landing-feature-narrative',
    standalone: true,
    imports: [TranslateDirective, ArtemisTranslatePipe, LandingChapterIntroComponent, LandingFeatureSectionComponent, LandingGlowbarComponent, LandingSupportStripComponent],
    template: `
        <section class="feature-narrative" aria-labelledby="feature-narrative-heading">
            <h2 id="feature-narrative-heading" class="visually-hidden" jhiTranslate="landing.narrative.heading"></h2>

            <div class="narrative-shell">
                <aside class="narrative-rail" [attr.aria-label]="'landing.narrative.heading' | artemisTranslate">
                    <nav class="rail-nav">
                        @for (chapter of chapters; track chapter.id) {
                            <div class="rail-chapter">
                                <span class="rail-chapter-label" [jhiTranslate]="'landing.narrative.contents.' + chapter.id"></span>
                                @for (sectionId of chapter.sectionIds; track sectionId) {
                                    <button
                                        type="button"
                                        class="rail-item"
                                        [class.active]="activeSectionId() === sectionId"
                                        [attr.aria-current]="activeSectionId() === sectionId ? 'true' : null"
                                        (click)="scrollToSection(sectionId)"
                                    >
                                        <span class="rail-dot" aria-hidden="true"></span>
                                        <span class="rail-label" [jhiTranslate]="'landing.narrative.sections.' + sectionId + '.navLabel'"></span>
                                    </button>
                                }
                            </div>
                        }
                    </nav>
                </aside>

                <div class="narrative-main">
                    <jhi-landing-chapter-intro chapterId="ai" />
                    @for (section of aiSections; track section.id) {
                        <jhi-landing-feature-section #sectionEl [section]="section" [attr.data-section-id]="section.id" />
                    }
                    <jhi-landing-support-strip stripId="ai" />

                    <jhi-landing-glowbar />

                    <jhi-landing-chapter-intro chapterId="assessment" />
                    @for (section of assessmentSections; track section.id) {
                        <jhi-landing-feature-section #sectionEl [section]="section" [attr.data-section-id]="section.id" />
                    }
                    <jhi-landing-support-strip stripId="assessment" />

                    <jhi-landing-glowbar />

                    <jhi-landing-chapter-intro chapterId="platform" />
                    @for (section of platformSections; track section.id) {
                        <jhi-landing-feature-section #sectionEl [section]="section" [attr.data-section-id]="section.id" />
                    }
                    <jhi-landing-support-strip stripId="platform" />
                </div>
            </div>
        </section>
    `,
    styles: `
        :host {
            display: block;
        }

        .feature-narrative {
            --narrative-rail-width: 190px;
            --narrative-rail-gap: clamp(2rem, 4vw, 4.5rem);
            position: relative;
            margin: 0 auto;
            padding: 0 2rem;
        }

        .narrative-shell {
            display: grid;
            grid-template-columns: var(--narrative-rail-width) minmax(0, 1fr);
            gap: var(--narrative-rail-gap);
            max-width: 1280px;
            margin: 0 auto;
        }

        .narrative-rail {
            position: sticky;
            top: 7rem;
            z-index: 10;
            align-self: start;
        }

        .rail-nav {
            display: flex;
            flex-direction: column;
            gap: 1.25rem;
            padding: 1rem 1rem 1rem 0;
        }

        .rail-chapter {
            display: flex;
            flex-direction: column;
            gap: 0.25rem;
        }

        .rail-chapter-label {
            font-size: 0.6875rem;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.1em;
            color: #475569;
            padding: 0.25rem 0;
            margin-bottom: 0.125rem;
        }

        .rail-item {
            display: flex;
            align-items: center;
            gap: 0.625rem;
            background: none;
            border: 0;
            color: #475569;
            cursor: pointer;
            text-align: left;
            font-size: 0.8125rem;
            font-weight: 500;
            padding: 0.25rem 0;
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

        .rail-dot {
            width: 5px;
            height: 5px;
            border-radius: 50%;
            background: currentColor;
            flex-shrink: 0;
            transition:
                transform 0.3s ease,
                background 0.3s ease;
        }

        .rail-item.active .rail-dot {
            transform: scale(1.6);
            background: #3b82f6;
        }

        .narrative-main {
            min-width: 0;
            overflow: visible;

            jhi-landing-glowbar {
                display: block;
                width: 100vw;
                max-width: none;
                margin-left: calc(50% - 50vw - ((var(--narrative-rail-width) + var(--narrative-rail-gap)) / 2));
            }
        }

        @media (max-width: 1024px) {
            .narrative-shell {
                grid-template-columns: 1fr;
            }

            .narrative-rail {
                display: none;
            }

            .narrative-main {
                jhi-landing-glowbar {
                    margin-left: calc(50% - 50vw);
                }
            }
        }

        @media (prefers-reduced-motion: reduce) {
            .rail-item,
            .rail-dot {
                transition: none;
            }
        }
    `,
})
export class LandingFeatureNarrativeComponent implements OnDestroy {
    private readonly host = inject(ElementRef<HTMLElement>);
    private readonly injector = inject(Injector);
    readonly sectionElements = viewChildren('sectionEl', { read: ElementRef<HTMLElement> });
    readonly activeSectionId = signal<string>('iris');

    private observer?: IntersectionObserver;
    private readonly visibilityRatios = new Map<HTMLElement, number>();

    readonly chapters = LANDING_CHAPTERS;
    readonly allSections = LANDING_SECTIONS;
    readonly aiSections: LandingFeatureSection[] = this.allSections.filter((section) => section.chapterId === 'ai');
    readonly assessmentSections: LandingFeatureSection[] = this.allSections.filter((section) => section.chapterId === 'assessment');
    readonly platformSections: LandingFeatureSection[] = this.allSections.filter((section) => section.chapterId === 'platform');

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
                    if (!(root instanceof HTMLElement)) return;

                    this.observer?.disconnect();
                    this.visibilityRatios.clear();

                    this.observer = new IntersectionObserver(
                        (entries) => {
                            for (const entry of entries) {
                                this.visibilityRatios.set(entry.target as HTMLElement, entry.intersectionRatio);
                            }

                            let maxRatio = -1;
                            let maxId = this.activeSectionId();
                            for (const section of sections) {
                                const el = section.nativeElement;
                                const ratio = this.visibilityRatios.get(el) ?? 0;
                                const sectionId = el.dataset['sectionId'];
                                if (ratio > maxRatio && sectionId) {
                                    maxRatio = ratio;
                                    maxId = sectionId;
                                }
                            }

                            if (maxRatio > 0) {
                                this.activeSectionId.set(maxId);
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

    scrollToSection(sectionId: string): void {
        const element = this.host.nativeElement.querySelector(`[data-section-id="${sectionId}"]`);
        const container = this.host.nativeElement.closest('.page-wrapper');
        if (!(element instanceof HTMLElement) || !(container instanceof HTMLElement)) return;

        const topOffset = 96;
        const top = element.getBoundingClientRect().top + container.scrollTop - topOffset;
        const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        container.scrollTo({ top, behavior: reducedMotion ? 'instant' : 'smooth' });
    }

    ngOnDestroy(): void {
        this.observer?.disconnect();
        this.visibilityRatios.clear();
    }
}
