import { Component } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { VisibleOnScrollDirective } from './visible-on-scroll.directive';

@Component({
    selector: 'jhi-landing-exams',
    standalone: true,
    imports: [TranslateDirective, ArtemisTranslatePipe, VisibleOnScrollDirective],
    template: `
        <section class="exams-section">
            <div class="container" jhiVisibleOnScroll>
                <div class="section-header">
                    <h2 class="section-title" jhiTranslate="landing.examMode.header.title"></h2>
                    <p class="section-subtitle" jhiTranslate="landing.examMode.header.subtitle"></p>
                </div>

                <div class="hero-image-wrapper">
                    <img src="content/images/landing/exam-participate.png" [alt]="'landing.examMode.imageAlt' | artemisTranslate" class="hero-image" loading="lazy" />
                </div>

                <p class="description" jhiTranslate="landing.examMode.description"></p>
            </div>

            <!-- Statistics -->
            <div class="split-section" jhiVisibleOnScroll>
                <div class="split-image">
                    <img src="content/images/landing/exam-stats.jpg" [alt]="'landing.examMode.statsImageAlt' | artemisTranslate" loading="lazy" />
                </div>
                <div class="split-text">
                    <h3 class="split-title" jhiTranslate="landing.examMode.statistics.title"></h3>
                    <p class="split-description" jhiTranslate="landing.examMode.statistics.description"></p>
                    <ul class="split-list">
                        <li jhiTranslate="landing.examMode.statistics.usingMetrics"></li>
                        <li jhiTranslate="landing.examMode.statistics.studentFeedback"></li>
                    </ul>
                </div>
            </div>

            <!-- ML-Assisted Assessment -->
            <div class="split-section reverse" jhiVisibleOnScroll>
                <div class="split-text">
                    <h3 class="split-title" jhiTranslate="landing.examMode.automaticAssessment.title"></h3>
                    <p class="split-description" jhiTranslate="landing.examMode.automaticAssessment.description"></p>
                    <ul class="split-list">
                        <li jhiTranslate="landing.examMode.automaticAssessment.automate"></li>
                        <li jhiTranslate="landing.examMode.automaticAssessment.quality"></li>
                    </ul>
                </div>
                <div class="split-image">
                    <img src="content/images/landing/exam-assessment.png" [alt]="'landing.examMode.assessmentImageAlt' | artemisTranslate" loading="lazy" />
                </div>
            </div>
        </section>
    `,
    styles: `
        .exams-section {
            max-width: 1280px;
            margin: 0 auto;
            padding: 0 1.25rem;
        }

        .container {
            opacity: 0.5;
            transform: translateY(20px);
            transition: all 0.5s ease-in-out;
        }

        :host .container.visible {
            opacity: 1;
            transform: translateY(0);
        }

        .section-header {
            padding-top: 4rem;
            text-align: center;
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

        .hero-image-wrapper {
            margin-top: 4rem;
            margin-bottom: -4.5rem;
            border-radius: 0.75rem;
            overflow: hidden;
            mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 1), rgba(0, 0, 0, 0));
            -webkit-mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 1), rgba(0, 0, 0, 0));
        }

        .hero-image {
            width: 100%;
            display: block;
            border-radius: 0.75rem;
            box-shadow:
                0 20px 25px -5px rgb(0 0 0 / 0.1),
                0 8px 10px -6px rgb(0 0 0 / 0.1);
        }

        .description {
            color: #e2e8f0;
            font-size: 1.5rem;
            line-height: 1.625;
            text-align: center;
            margin-inline-start: 2rem;
            margin-inline-end: 2rem;
        }

        .split-section {
            display: flex;
            flex-direction: column;
            gap: 4rem;
            width: 100%;
            margin-top: 2rem;
            margin-bottom: 4rem;
            opacity: 0.5;
            transform: translateY(20px);
            transition: all 0.5s ease-in-out;
        }

        :host .split-section.visible {
            opacity: 1;
            transform: translateY(0);
        }

        .split-section.reverse {
            direction: rtl;

            > * {
                direction: ltr;
            }
        }

        .split-image {
            flex: 1;
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .split-image img {
            width: 100%;
            border-radius: 0.5rem;
            box-shadow:
                0 20px 25px -5px rgb(0 0 0 / 0.1),
                0 8px 10px -6px rgb(0 0 0 / 0.1);
        }

        .split-text {
            flex: 1;
            display: flex;
            flex-direction: column;
            gap: 1rem;
        }

        .split-title {
            font-size: 1.25rem;
            line-height: 1.75rem;
            font-weight: 600;
            color: white;
            text-align: center;
        }

        .split-description {
            color: #e2e8f0;
            line-height: 1.625;
        }

        .split-list {
            color: #e2e8f0;
            padding-left: 1.25rem;
            line-height: 1.625;

            li {
                margin-bottom: 0.5rem;
            }
        }

        @media (min-width: 1024px) {
            .split-section {
                flex-direction: row;
            }

            .section-title {
                font-size: 3rem;
                line-height: 1;
                letter-spacing: -0.025em;
            }
        }

        @media (max-width: 1023px) {
            .split-section.reverse {
                direction: ltr;
            }
        }
    `,
})
export class LandingExamsComponent {}
