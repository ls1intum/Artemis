import { Component, inject, input } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { VisibleOnScrollDirective } from './visible-on-scroll.directive';
import { ILLUSTRATION_COLORS, LANGUAGE_ICONS_RAW, LandingFeatureSection, LanguageIcon } from './landing-feature-data';

@Component({
    selector: 'jhi-landing-feature-section',
    standalone: true,
    imports: [TranslateDirective, ArtemisTranslatePipe, VisibleOnScrollDirective],
    template: `
        <section
            class="feature-section"
            [id]="'feature-' + section().id"
            [class.reverse]="section().reverse"
            [class.deep-dive]="section().variant === 'deep-dive'"
            [style.scroll-margin-top.rem]="6"
            jhiVisibleOnScroll
        >
            <div class="feature-main">
                <div class="feature-copy">
                    <span class="feature-badge" [jhiTranslate]="'landing.narrative.sections.' + section().id + '.badge'"></span>
                    <h3 class="feature-title" [jhiTranslate]="'landing.narrative.sections.' + section().id + '.title'"></h3>
                    <p class="feature-description" [jhiTranslate]="'landing.narrative.sections.' + section().id + '.description'"></p>
                    @if (section().bullets.length) {
                        <ul class="feature-bullets">
                            @for (j of section().bullets; track j) {
                                <li [jhiTranslate]="'landing.narrative.sections.' + section().id + '.bullets.' + j"></li>
                            }
                        </ul>
                    }
                </div>
                <div class="feature-visual">
                    @if (section().visualType === 'image') {
                        <div class="image-frame">
                            <img [src]="section().imageSrc!" [alt]="section().imageAltKey! | artemisTranslate" loading="lazy" />
                        </div>
                    } @else {
                        <div class="illustration-card" [style.--glow-color]="glowColor" [attr.data-feature]="section().id" aria-hidden="true">
                            <div class="illustration-glow"></div>
                            <div class="illustration-grid"></div>
                            <div class="illustration-icon" [innerHTML]="section().illustrationIcon"></div>
                            @switch (section().id) {
                                @case ('iris') {
                                    <div class="illustration-scene scene-iris">
                                        <div class="scene-panel">
                                            <span class="scene-chip chip-accent"></span>
                                            <span class="scene-line line-long"></span>
                                            <span class="scene-line line-mid"></span>
                                            <div class="scene-chat user">
                                                <span class="scene-line line-short"></span>
                                            </div>
                                            <div class="scene-chat assistant">
                                                <span class="scene-line line-mid"></span>
                                                <span class="scene-line line-long"></span>
                                                <span class="scene-line line-short"></span>
                                            </div>
                                        </div>
                                    </div>
                                }
                                @case ('atlas') {
                                    <div class="illustration-scene scene-atlas">
                                        <div class="path-card">
                                            <div class="path-track">
                                                <span class="path-node active"></span>
                                                <span class="path-link"></span>
                                                <span class="path-node"></span>
                                                <span class="path-link"></span>
                                                <span class="path-node"></span>
                                            </div>
                                            <div class="path-track secondary">
                                                <span class="path-node"></span>
                                                <span class="path-link"></span>
                                                <span class="path-node active"></span>
                                                <span class="path-link"></span>
                                                <span class="path-node"></span>
                                            </div>
                                            <div class="mini-chart">
                                                <span class="mini-bar bar-sm"></span>
                                                <span class="mini-bar bar-lg"></span>
                                                <span class="mini-bar bar-md"></span>
                                            </div>
                                        </div>
                                    </div>
                                }
                                @case ('athena') {
                                    <div class="illustration-scene scene-athena">
                                        <div class="review-card">
                                            <span class="scene-chip chip-soft"></span>
                                            <span class="scene-line line-long"></span>
                                            <span class="scene-line line-mid"></span>
                                            <div class="review-row">
                                                <span class="review-pill positive"></span>
                                                <span class="review-pill neutral"></span>
                                            </div>
                                        </div>
                                        <div class="review-card offset">
                                            <span class="scene-chip chip-accent"></span>
                                            <span class="scene-line line-mid"></span>
                                            <span class="scene-line line-short"></span>
                                            <div class="review-row">
                                                <span class="review-pill positive"></span>
                                                <span class="review-pill positive"></span>
                                            </div>
                                        </div>
                                    </div>
                                }
                                @case ('hyperion') {
                                    <div class="illustration-scene scene-hyperion">
                                        <div class="generator-input">
                                            <span class="scene-line line-long"></span>
                                            <span class="scene-line line-mid"></span>
                                        </div>
                                        <div class="generator-output">
                                            <div class="output-card">
                                                <span class="scene-line line-mid"></span>
                                                <span class="scene-line line-short"></span>
                                            </div>
                                            <div class="output-card">
                                                <span class="scene-line line-long"></span>
                                                <span class="scene-line line-short"></span>
                                            </div>
                                            <div class="output-card">
                                                <span class="scene-line line-mid"></span>
                                                <span class="scene-line line-mid"></span>
                                            </div>
                                        </div>
                                    </div>
                                }
                            }
                        </div>
                    }
                </div>
            </div>

            @if (section().subcontent === 'programming-details') {
                <div class="subcontent" jhiVisibleOnScroll>
                    <div class="integration-cards">
                        <div class="integration-card">
                            <img src="content/images/landing/logo.png" alt="Artemis" class="card-logo" />
                            <h5 jhiTranslate="landing.narrative.sections.programming.cards.git.title"></h5>
                            <p jhiTranslate="landing.narrative.sections.programming.cards.git.description"></p>
                        </div>
                        <div class="integration-card">
                            <img src="content/images/landing/logo.png" alt="Artemis" class="card-logo" />
                            <h5 jhiTranslate="landing.narrative.sections.programming.cards.ci.title"></h5>
                            <p jhiTranslate="landing.narrative.sections.programming.cards.ci.description"></p>
                        </div>
                        <div class="integration-card">
                            <img src="content/images/landing/logo.png" alt="Artemis" class="card-logo" />
                            <h5 jhiTranslate="landing.narrative.sections.programming.cards.batteries.title"></h5>
                            <p jhiTranslate="landing.narrative.sections.programming.cards.batteries.description"></p>
                        </div>
                    </div>
                    <div class="languages-row">
                        <p class="languages-label" jhiTranslate="landing.narrative.sections.programming.programmingLanguages"></p>
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

            @if (section().subcontent === 'exam-details') {
                <div class="subcontent" jhiVisibleOnScroll>
                    <div class="exam-cards">
                        <div class="exam-card">
                            <div class="exam-card-image">
                                <img src="content/images/landing/exam-stats.jpg" [alt]="'landing.narrative.sections.exams.statsImageAlt' | artemisTranslate" loading="lazy" />
                            </div>
                            <div class="exam-card-text">
                                <h5 jhiTranslate="landing.narrative.sections.exams.statistics.title"></h5>
                                <p jhiTranslate="landing.narrative.sections.exams.statistics.description"></p>
                            </div>
                        </div>
                        <div class="exam-card">
                            <div class="exam-card-image">
                                <img
                                    src="content/images/landing/exam-assessment.png"
                                    [alt]="'landing.narrative.sections.exams.assessmentImageAlt' | artemisTranslate"
                                    loading="lazy"
                                />
                            </div>
                            <div class="exam-card-text">
                                <h5 jhiTranslate="landing.narrative.sections.exams.automaticAssessment.title"></h5>
                                <p jhiTranslate="landing.narrative.sections.exams.automaticAssessment.description"></p>
                            </div>
                        </div>
                    </div>
                </div>
            }

            @if (section().subcontent === 'integrity-analytics-details') {
                <div class="subcontent" jhiVisibleOnScroll>
                    <div class="integrity-grid">
                        <div class="integrity-card">
                            <div class="integrity-icon" aria-hidden="true">
                                <svg
                                    xmlns="http://www.w3.org/2000/svg"
                                    viewBox="0 0 24 24"
                                    fill="none"
                                    stroke="currentColor"
                                    stroke-width="1.5"
                                    stroke-linecap="round"
                                    stroke-linejoin="round"
                                >
                                    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                                </svg>
                            </div>
                            <h5 jhiTranslate="landing.narrative.sections.integrity.plagiarism.title"></h5>
                            <p jhiTranslate="landing.narrative.sections.integrity.plagiarism.description"></p>
                        </div>
                        <div class="integrity-card">
                            <div class="integrity-icon" aria-hidden="true">
                                <svg
                                    xmlns="http://www.w3.org/2000/svg"
                                    viewBox="0 0 24 24"
                                    fill="none"
                                    stroke="currentColor"
                                    stroke-width="1.5"
                                    stroke-linecap="round"
                                    stroke-linejoin="round"
                                >
                                    <line x1="18" y1="20" x2="18" y2="10" />
                                    <line x1="12" y1="20" x2="12" y2="4" />
                                    <line x1="6" y1="20" x2="6" y2="14" />
                                </svg>
                            </div>
                            <h5 jhiTranslate="landing.narrative.sections.integrity.analytics.title"></h5>
                            <p jhiTranslate="landing.narrative.sections.integrity.analytics.description"></p>
                        </div>
                    </div>
                </div>
            }
        </section>
    `,
    styles: `
        :host {
            display: block;
        }

        .feature-section {
            padding: 4rem 0;
            border-bottom: 1px solid rgba(30, 41, 59, 0.5);
            opacity: 0;
            transform: translateY(24px);
            transition:
                opacity 0.6s ease-out,
                transform 0.6s ease-out;

            &:last-of-type {
                border-bottom: none;
            }
        }

        .feature-section.deep-dive {
            padding-bottom: 5rem;
        }

        :host .feature-section.visible {
            opacity: 1;
            transform: translateY(0);
        }

        .feature-main {
            display: flex;
            align-items: center;
            gap: 3rem;
        }

        .reverse .feature-main {
            flex-direction: row-reverse;
        }

        .feature-copy {
            flex: 1 1 0;
            min-width: 280px;
        }

        .feature-visual {
            flex: 0 0 48%;
            min-width: 0;
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .image-frame {
            position: relative;
            width: 100%;
            border-radius: 16px;
            overflow: hidden;
            border: 1px solid rgba(255, 255, 255, 0.06);
            box-shadow:
                0 25px 50px -12px rgba(0, 0, 0, 0.5),
                0 0 0 1px rgba(255, 255, 255, 0.04);
        }

        .image-frame::before {
            content: '';
            position: absolute;
            inset: 0;
            z-index: 1;
            pointer-events: none;
            background: linear-gradient(180deg, rgba(7, 11, 21, 0.1), rgba(7, 11, 21, 0));
        }

        .feature-visual img {
            display: block;
            width: 100%;
            height: auto;
        }

        .feature-badge {
            display: inline-block;
            font-size: 0.6875rem;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.12em;
            color: #60a5fa;
            margin-bottom: 0.75rem;
        }

        .feature-title {
            font-size: 1.75rem;
            font-weight: 700;
            color: #fff;
            margin: 0 0 0.875rem;
            line-height: 1.25;
        }

        .feature-description {
            font-size: 0.9375rem;
            line-height: 1.7;
            color: #94a3b8;
            margin: 0 0 1.25rem;
        }

        .feature-bullets {
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
            background: linear-gradient(180deg, rgba(18, 28, 45, 0.96), rgba(9, 14, 26, 0.98)), #0c1425;
            border: 1px solid rgba(255, 255, 255, 0.06);
            display: flex;
            align-items: center;
            justify-content: center;
            overflow: hidden;
        }

        .illustration-card::before {
            content: '';
            position: absolute;
            inset: 0;
            background: linear-gradient(130deg, rgba(255, 255, 255, 0.08), transparent 30%), radial-gradient(circle at bottom right, rgba(255, 255, 255, 0.04), transparent 38%);
            pointer-events: none;
        }

        .illustration-glow {
            position: absolute;
            inset: -20%;
            opacity: 0.5;
            border-radius: inherit;
            filter: blur(54px);
            background: radial-gradient(ellipse at 45% 50%, var(--glow-color, #3b82f6), transparent 65%);
        }

        .illustration-grid {
            position: absolute;
            inset: 0;
            background-image: linear-gradient(rgba(148, 163, 184, 0.07) 1px, transparent 1px), linear-gradient(90deg, rgba(148, 163, 184, 0.07) 1px, transparent 1px);
            background-size: 24px 24px;
            mask-image: linear-gradient(180deg, transparent, black 25%, black 75%, transparent);
            opacity: 0.35;
        }

        .illustration-icon {
            z-index: 0;
            position: absolute;
            right: 2rem;
            bottom: 1.75rem;
            color: rgba(255, 255, 255, 0.08);

            svg {
                width: 72px;
                height: 72px;
            }
        }

        .illustration-scene {
            position: relative;
            z-index: 1;
            width: min(84%, 480px);
        }

        .scene-panel,
        .path-card,
        .generator-input,
        .review-card,
        .output-card {
            border-radius: 18px;
            border: 1px solid rgba(255, 255, 255, 0.12);
            background: rgba(15, 23, 42, 0.72);
            box-shadow:
                inset 0 1px 0 rgba(255, 255, 255, 0.06),
                0 18px 40px -24px rgba(15, 23, 42, 0.9);
            backdrop-filter: blur(10px);
            -webkit-backdrop-filter: blur(10px);
        }

        .scene-line {
            display: block;
            height: 10px;
            border-radius: 999px;
            background: linear-gradient(90deg, rgba(226, 232, 240, 0.92), rgba(226, 232, 240, 0.16));
        }

        .line-long {
            width: 72%;
        }

        .line-mid {
            width: 56%;
        }

        .line-short {
            width: 34%;
        }

        .scene-chip {
            display: inline-flex;
            width: 88px;
            height: 24px;
            border-radius: 999px;
            background: rgba(255, 255, 255, 0.08);
        }

        .chip-accent {
            background: rgba(96, 165, 250, 0.26);
            box-shadow: 0 0 0 1px rgba(96, 165, 250, 0.25);
        }

        .chip-soft {
            background: rgba(148, 163, 184, 0.18);
        }

        .scene-iris .scene-panel {
            width: 100%;
            padding: 1.4rem;
            display: flex;
            flex-direction: column;
            gap: 0.75rem;
        }

        .scene-chat {
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
            padding: 0.9rem 1rem;
            border-radius: 16px;
        }

        .scene-chat.user {
            align-self: flex-end;
            width: 52%;
            background: rgba(148, 163, 184, 0.12);
        }

        .scene-chat.assistant {
            width: 78%;
            background: rgba(59, 130, 246, 0.15);
            box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.18);
        }

        .scene-atlas .path-card {
            width: 100%;
            padding: 1.5rem;
        }

        .path-track {
            display: flex;
            align-items: center;
            gap: 0.6rem;
        }

        .path-track + .path-track {
            margin-top: 1rem;
        }

        .path-track.secondary {
            margin-left: 2.4rem;
        }

        .path-node {
            width: 12px;
            height: 12px;
            border-radius: 50%;
            background: rgba(226, 232, 240, 0.35);
            box-shadow: 0 0 0 6px rgba(255, 255, 255, 0.03);
        }

        .path-node.active {
            background: #a78bfa;
            box-shadow: 0 0 0 8px rgba(167, 139, 250, 0.14);
        }

        .path-link {
            flex: 1;
            height: 2px;
            background: linear-gradient(90deg, rgba(167, 139, 250, 0.7), rgba(255, 255, 255, 0.18));
        }

        .mini-chart {
            margin-top: 1.35rem;
            display: flex;
            align-items: flex-end;
            gap: 0.55rem;
            height: 56px;
        }

        .mini-bar {
            width: 16px;
            border-radius: 999px 999px 6px 6px;
            background: linear-gradient(180deg, rgba(167, 139, 250, 0.95), rgba(59, 130, 246, 0.45));
        }

        .bar-sm {
            height: 22px;
        }

        .bar-md {
            height: 34px;
        }

        .bar-lg {
            height: 52px;
        }

        .scene-athena {
            width: min(88%, 500px);
            display: grid;
            gap: 1rem;
        }

        .review-card {
            padding: 1.15rem 1.2rem;
            display: flex;
            flex-direction: column;
            gap: 0.7rem;
        }

        .review-card.offset {
            margin-left: 2rem;
        }

        .review-row {
            display: flex;
            gap: 0.55rem;
        }

        .review-pill {
            height: 20px;
            border-radius: 999px;
            background: rgba(148, 163, 184, 0.2);
        }

        .review-pill.positive {
            width: 88px;
            background: rgba(34, 197, 94, 0.24);
        }

        .review-pill.neutral {
            width: 58px;
        }

        .scene-hyperion {
            width: min(88%, 500px);
            display: flex;
            flex-direction: column;
            gap: 1rem;
        }

        .generator-input {
            padding: 1.2rem;
            display: flex;
            flex-direction: column;
            gap: 0.65rem;
        }

        .generator-output {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 0.85rem;
        }

        .output-card {
            min-height: 92px;
            padding: 1rem;
            display: flex;
            flex-direction: column;
            justify-content: center;
            gap: 0.6rem;
        }

        /* Subcontent shared */
        .subcontent {
            margin-top: 3rem;
            opacity: 0;
            transform: translateY(24px);
            transition:
                opacity 0.6s ease-out,
                transform 0.6s ease-out;
        }

        :host .subcontent.visible {
            opacity: 1;
            transform: translateY(0);
        }

        /* Programming subcontent */
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

            h5 {
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

        /* Exam subcontent */
        .exam-cards {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 1.5rem;
        }

        .exam-card {
            background: #0f172a;
            border: 1px solid #1e293b;
            border-radius: 12px;
            overflow: hidden;

            .exam-card-image {
                width: 100%;
                aspect-ratio: 16 / 10;
                overflow: hidden;
                border-bottom: 1px solid #1e293b;

                img {
                    width: 100%;
                    height: 100%;
                    object-fit: cover;
                    object-position: top left;
                    display: block;
                }
            }

            .exam-card-text {
                padding: 1.25rem;
            }

            h5 {
                font-size: 0.9375rem;
                font-weight: 600;
                color: #fff;
                margin: 0 0 0.375rem;
            }

            p {
                color: #94a3b8;
                font-size: 0.8125rem;
                line-height: 1.5;
                margin: 0;
            }
        }

        /* Integrity & Analytics subcontent */
        .integrity-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 1.5rem;
        }

        .integrity-card {
            background: #0f172a;
            border: 1px solid #1e293b;
            border-radius: 10px;
            padding: 1.5rem;

            h5 {
                font-size: 1rem;
                font-weight: 700;
                color: #fff;
                margin: 0.75rem 0 0.5rem;
            }

            p {
                font-size: 0.875rem;
                color: #94a3b8;
                line-height: 1.6;
                margin: 0;
            }
        }

        .integrity-icon {
            color: #6366f1;

            svg {
                width: 28px;
                height: 28px;
            }
        }

        @media (max-width: 1024px) {
            .feature-main,
            .reverse .feature-main {
                flex-direction: column;
                gap: 2rem;
            }

            .feature-visual {
                flex: 0 0 auto;
            }

            .feature-title {
                font-size: 1.5rem;
            }

            .feature-section {
                padding: 3rem 0;
            }

            .integration-cards {
                grid-template-columns: 1fr;
            }

            .exam-cards {
                grid-template-columns: 1fr;
            }

            .integrity-grid {
                grid-template-columns: 1fr;
            }

            .generator-output {
                grid-template-columns: 1fr;
            }

            .review-card.offset {
                margin-left: 0;
            }
        }

        @media (prefers-reduced-motion: reduce) {
            .feature-section,
            .subcontent {
                transition: none;
                opacity: 1;
                transform: none;
            }
        }
    `,
})
export class LandingFeatureSectionComponent {
    private readonly sanitizer = inject(DomSanitizer);

    section = input.required<LandingFeatureSection>();

    readonly languages: LanguageIcon[] = LANGUAGE_ICONS_RAW.map((lang) => ({
        name: lang.name,
        href: lang.href,
        sizeClass: lang.sizeClass,
        icon: this.sanitizer.bypassSecurityTrustHtml(lang.svg),
    }));

    get glowColor(): string {
        return ILLUSTRATION_COLORS[this.section().id] ?? '#3b82f6';
    }
}
