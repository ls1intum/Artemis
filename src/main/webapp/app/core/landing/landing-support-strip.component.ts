import { Component, computed, inject, input } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { VisibleOnScrollDirective } from './visible-on-scroll.directive';

type SupportStripId = 'ai' | 'assessment' | 'platform';

interface SupportItemConfig {
    id: string;
    imageSrc?: string;
    imageAltKey?: string;
    iconSvg?: string;
}

const SUPPORT_ITEMS: Record<SupportStripId, SupportItemConfig[]> = {
    ai: [
        {
            id: 'tutorSuggestions',
            imageSrc: 'content/images/landing/tutor-suggestions-view.png',
            imageAltKey: 'landing.narrative.support.ai.items.tutorSuggestions.imageAlt',
        },
        {
            id: 'lectureAwareAi',
            imageSrc: 'content/images/landing/processed-lecture-units.png',
            imageAltKey: 'landing.narrative.support.ai.items.lectureAwareAi.imageAlt',
        },
        {
            id: 'persistentContext',
            iconSvg:
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3v5h5"/><path d="M3.05 13A9 9 0 1 0 6 5.3L3 8"/></svg>',
        },
    ],
    assessment: [
        {
            id: 'teamExercises',
            iconSvg:
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
        },
        {
            id: 'fileUpload',
            iconSvg:
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>',
        },
        {
            id: 'grading',
            imageSrc: 'content/images/landing/assessment-dashboard.png',
            imageAltKey: 'landing.narrative.support.assessment.items.grading.imageAlt',
        },
    ],
    platform: [
        {
            id: 'tutorialGroups',
            imageSrc: 'content/images/landing/tutorial-groups-overview.png',
            imageAltKey: 'landing.narrative.support.platform.items.tutorialGroups.imageAlt',
        },
        {
            id: 'lti',
            iconSvg:
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M10 13a5 5 0 0 0 7.54.54l2.92-2.92a5 5 0 0 0-7.07-7.07L11.7 5.24"/><path d="M14 11a5 5 0 0 0-7.54-.54L3.54 13.4a5 5 0 0 0 7.07 7.07l1.67-1.67"/></svg>',
        },
        {
            id: 'openSource',
            iconSvg:
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2a3 3 0 0 0-3 3c0 1.1.6 2.06 1.5 2.58v3.16A6 6 0 0 0 6 16.5V18a4 4 0 0 0 8 0v-1.5a6 6 0 0 0-2.5-4.76V7.58A2.99 2.99 0 0 0 15 5a3 3 0 0 0-3-3Z"/><path d="M8 22h8"/></svg>',
        },
        {
            id: 'scalable',
            iconSvg:
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="6" rx="2"/><rect x="3" y="15" width="18" height="6" rx="2"/><path d="M7 6h.01"/><path d="M7 18h.01"/><path d="M11 6h6"/><path d="M11 18h6"/></svg>',
        },
    ],
};

@Component({
    selector: 'jhi-landing-support-strip',
    standalone: true,
    imports: [TranslateDirective, ArtemisTranslatePipe, VisibleOnScrollDirective],
    template: `
        <section class="support-strip" [class.support-strip-platform]="stripId() === 'platform'" jhiVisibleOnScroll>
            <div class="support-header">
                <span class="support-eyebrow" [jhiTranslate]="'landing.narrative.support.' + stripId() + '.eyebrow'"></span>
                <h4 class="support-title" [jhiTranslate]="'landing.narrative.support.' + stripId() + '.title'"></h4>
                <p class="support-description" [jhiTranslate]="'landing.narrative.support.' + stripId() + '.description'"></p>
            </div>

            <div class="support-grid">
                @for (item of items(); track item.id) {
                    <article class="support-card" [class.support-card-image]="!!item.imageSrc">
                        @if (item.imageSrc) {
                            <div class="support-media">
                                <img [src]="item.imageSrc" [alt]="item.imageAltKey! | artemisTranslate" loading="lazy" />
                            </div>
                        } @else {
                            <div class="support-media support-media-illustrated" [attr.data-strip]="stripId()" [attr.data-item]="item.id" aria-hidden="true">
                                <div class="support-illustration-glow"></div>
                                <div class="support-illustration-grid"></div>
                                <div class="support-icon" [innerHTML]="item.icon"></div>
                                @switch (item.id) {
                                    @case ('persistentContext') {
                                        <div class="support-scene scene-memory">
                                            <div class="memory-node active"></div>
                                            <div class="memory-link"></div>
                                            <div class="memory-node"></div>
                                            <div class="memory-link"></div>
                                            <div class="memory-node"></div>
                                        </div>
                                    }
                                    @case ('teamExercises') {
                                        <div class="support-scene scene-team">
                                            <div class="team-avatar avatar-a"></div>
                                            <div class="team-avatar avatar-b"></div>
                                            <div class="team-avatar avatar-c"></div>
                                            <div class="team-rail"></div>
                                        </div>
                                    }
                                    @case ('fileUpload') {
                                        <div class="support-scene scene-upload">
                                            <div class="upload-panel">
                                                <div class="upload-chip"></div>
                                                <div class="upload-line line-long"></div>
                                                <div class="upload-line line-mid"></div>
                                            </div>
                                        </div>
                                    }
                                    @case ('lti') {
                                        <div class="support-scene scene-link">
                                            <div class="link-card left"></div>
                                            <div class="link-bridge"></div>
                                            <div class="link-card right"></div>
                                        </div>
                                    }
                                    @case ('openSource') {
                                        <div class="support-scene scene-open">
                                            <div class="open-ring"></div>
                                            <div class="open-ring secondary"></div>
                                        </div>
                                    }
                                    @case ('scalable') {
                                        <div class="support-scene scene-scale">
                                            <div class="scale-stack stack-top"></div>
                                            <div class="scale-stack stack-mid"></div>
                                            <div class="scale-stack stack-low"></div>
                                        </div>
                                    }
                                }
                            </div>
                        }

                        <div class="support-copy">
                            <h5 [jhiTranslate]="'landing.narrative.support.' + stripId() + '.items.' + item.id + '.title'"></h5>
                            <p [jhiTranslate]="'landing.narrative.support.' + stripId() + '.items.' + item.id + '.description'"></p>
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

        .support-strip {
            padding: 2.5rem 0 1.5rem;
            opacity: 0;
            transform: translateY(24px);
            transition:
                opacity 0.6s ease-out,
                transform 0.6s ease-out;
        }

        :host .support-strip.visible {
            opacity: 1;
            transform: translateY(0);
        }

        .support-header {
            max-width: 760px;
            margin-bottom: 1.5rem;
        }

        .support-eyebrow {
            display: inline-block;
            margin-bottom: 0.65rem;
            color: #60a5fa;
            font-size: 0.6875rem;
            font-weight: 700;
            letter-spacing: 0.14em;
            text-transform: uppercase;
        }

        .support-title {
            margin: 0;
            color: #fff;
            font-size: 1.45rem;
            font-weight: 700;
            line-height: 1.2;
        }

        .support-description {
            margin: 0.65rem 0 0;
            color: #94a3b8;
            font-size: 0.9375rem;
            line-height: 1.7;
        }

        .support-grid {
            display: grid;
            grid-template-columns: repeat(3, minmax(0, 1fr));
            gap: 1rem;
        }

        .support-strip-platform .support-grid {
            grid-template-columns: repeat(4, minmax(0, 1fr));
        }

        .support-card {
            display: flex;
            flex-direction: column;
            gap: 1rem;
            min-width: 0;
            padding: 1.15rem;
            border-radius: 16px;
            border: 1px solid rgba(30, 41, 59, 0.82);
            background: linear-gradient(180deg, rgba(10, 16, 28, 0.86), rgba(7, 11, 22, 0.94));
            box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
        }

        .support-card-image {
            overflow: hidden;
        }

        .support-media {
            margin: -1.15rem -1.15rem 0;
            aspect-ratio: 16 / 9;
            overflow: hidden;
            border-bottom: 1px solid rgba(30, 41, 59, 0.82);
            background: #020617;
            position: relative;
        }

        .support-media img {
            display: block;
            width: 100%;
            height: 100%;
            object-fit: cover;
            object-position: top left;
        }

        .support-media-illustrated {
            display: flex;
            align-items: center;
            justify-content: center;
            background: linear-gradient(180deg, rgba(15, 23, 42, 0.98), rgba(7, 11, 22, 0.98));
        }

        .support-illustration-glow {
            position: absolute;
            inset: -22%;
            background: radial-gradient(circle at center, rgba(96, 165, 250, 0.34), transparent 60%);
            filter: blur(36px);
            opacity: 0.9;
        }

        .support-illustration-grid {
            position: absolute;
            inset: 0;
            opacity: 0.26;
            background-image: linear-gradient(rgba(148, 163, 184, 0.08) 1px, transparent 1px), linear-gradient(90deg, rgba(148, 163, 184, 0.08) 1px, transparent 1px);
            background-size: 22px 22px;
            mask-image: linear-gradient(180deg, transparent, black 25%, black 75%, transparent);
        }

        .support-icon {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 2.75rem;
            height: 2.75rem;
            border-radius: 12px;
            color: #93c5fd;
            background: rgba(30, 41, 59, 0.62);
            box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.06);
            position: absolute;
            top: 0.9rem;
            right: 0.9rem;
            z-index: 2;
        }

        .support-icon svg {
            width: 1.35rem;
            height: 1.35rem;
        }

        .support-scene {
            position: relative;
            z-index: 1;
            width: min(78%, 240px);
            height: 70%;
        }

        .scene-memory {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 0.85rem;
        }

        .memory-node {
            width: 18px;
            height: 18px;
            border-radius: 50%;
            background: rgba(226, 232, 240, 0.34);
            box-shadow: 0 0 0 10px rgba(255, 255, 255, 0.04);
        }

        .memory-node.active {
            background: #60a5fa;
            box-shadow: 0 0 0 12px rgba(96, 165, 250, 0.16);
        }

        .memory-link {
            width: 52px;
            height: 2px;
            background: linear-gradient(90deg, rgba(96, 165, 250, 0.8), rgba(148, 163, 184, 0.2));
        }

        .scene-team {
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .team-avatar {
            position: absolute;
            width: 44px;
            height: 44px;
            border-radius: 50%;
            background: linear-gradient(180deg, rgba(96, 165, 250, 0.95), rgba(59, 130, 246, 0.5));
            box-shadow: 0 10px 30px rgba(15, 23, 42, 0.35);
        }

        .avatar-a {
            left: 22%;
            top: 34%;
        }

        .avatar-b {
            left: calc(50% - 22px);
            top: 18%;
            background: linear-gradient(180deg, rgba(167, 139, 250, 0.95), rgba(96, 165, 250, 0.45));
        }

        .avatar-c {
            right: 22%;
            top: 34%;
            background: linear-gradient(180deg, rgba(34, 197, 94, 0.95), rgba(20, 184, 166, 0.45));
        }

        .team-rail {
            position: absolute;
            inset: 48% 28% auto;
            height: 2px;
            background: linear-gradient(90deg, rgba(96, 165, 250, 0.45), rgba(167, 139, 250, 0.7), rgba(34, 197, 94, 0.45));
        }

        .scene-upload {
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .upload-panel {
            width: min(82%, 210px);
            padding: 1rem;
            border-radius: 18px;
            border: 1px solid rgba(255, 255, 255, 0.12);
            background: rgba(15, 23, 42, 0.76);
            box-shadow:
                inset 0 1px 0 rgba(255, 255, 255, 0.06),
                0 18px 30px rgba(15, 23, 42, 0.34);
        }

        .upload-chip {
            width: 88px;
            height: 24px;
            border-radius: 999px;
            margin-bottom: 0.8rem;
            background: rgba(96, 165, 250, 0.26);
        }

        .upload-line {
            height: 10px;
            border-radius: 999px;
            background: linear-gradient(90deg, rgba(226, 232, 240, 0.92), rgba(226, 232, 240, 0.18));
        }

        .upload-line + .upload-line {
            margin-top: 0.55rem;
        }

        .line-long {
            width: 72%;
        }

        .line-mid {
            width: 54%;
        }

        .scene-link {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 0.75rem;
        }

        .link-card {
            width: 76px;
            height: 64px;
            border-radius: 16px;
            border: 1px solid rgba(255, 255, 255, 0.12);
            background: rgba(15, 23, 42, 0.76);
        }

        .link-bridge {
            width: 44px;
            height: 2px;
            background: linear-gradient(90deg, rgba(96, 165, 250, 0.8), rgba(148, 163, 184, 0.22));
        }

        .scene-open,
        .scene-scale {
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .open-ring {
            width: 72px;
            height: 72px;
            border-radius: 50%;
            border: 2px solid rgba(96, 165, 250, 0.8);
            box-shadow: 0 0 0 18px rgba(96, 165, 250, 0.08);
        }

        .open-ring.secondary {
            position: absolute;
            width: 120px;
            height: 120px;
            border-color: rgba(167, 139, 250, 0.35);
            box-shadow: none;
        }

        .scene-scale {
            gap: 0.75rem;
            align-items: flex-end;
        }

        .scale-stack {
            width: 58px;
            border-radius: 14px 14px 8px 8px;
            background: linear-gradient(180deg, rgba(96, 165, 250, 0.92), rgba(15, 23, 42, 0.7));
            border: 1px solid rgba(255, 255, 255, 0.12);
        }

        .stack-top {
            height: 96px;
        }

        .stack-mid {
            height: 72px;
        }

        .stack-low {
            height: 48px;
        }

        .support-copy h5 {
            margin: 0 0 0.45rem;
            color: #fff;
            font-size: 0.95rem;
            font-weight: 700;
            line-height: 1.35;
        }

        .support-copy p {
            margin: 0;
            color: #94a3b8;
            font-size: 0.8125rem;
            line-height: 1.6;
        }

        @media (max-width: 1180px) {
            .support-grid,
            .support-strip-platform .support-grid {
                grid-template-columns: repeat(2, minmax(0, 1fr));
            }
        }

        @media (max-width: 768px) {
            .support-grid,
            .support-strip-platform .support-grid {
                grid-template-columns: 1fr;
            }
        }

        @media (prefers-reduced-motion: reduce) {
            .support-strip {
                transition: none;
                opacity: 1;
                transform: none;
            }
        }
    `,
})
export class LandingSupportStripComponent {
    private readonly sanitizer = inject(DomSanitizer);

    stripId = input.required<SupportStripId>();

    readonly items = computed(() =>
        SUPPORT_ITEMS[this.stripId()].map((item) => ({
            ...item,
            icon: item.iconSvg ? this.sanitizer.bypassSecurityTrustHtml(item.iconSvg) : ('' as SafeHtml),
        })),
    );
}
