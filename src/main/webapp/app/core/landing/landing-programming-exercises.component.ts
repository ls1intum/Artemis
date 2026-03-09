import { Component, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { VisibleOnScrollDirective } from './visible-on-scroll.directive';

@Component({
    selector: 'jhi-landing-programming-exercises',
    standalone: true,
    imports: [TranslateDirective, ArtemisTranslatePipe, VisibleOnScrollDirective],
    template: `
        <section class="programming-section">
            <!-- Header + Screenshot -->
            <div class="container" jhiVisibleOnScroll>
                <div class="section-header">
                    <h2 class="section-title" jhiTranslate="landing.programmingExercises.header.title"></h2>
                    <p class="section-subtitle" jhiTranslate="landing.programmingExercises.header.subtitle"></p>
                </div>

                <div class="hero-image-wrapper">
                    <img src="content/images/landing/editor.png" [alt]="'landing.programmingExercises.imageAlt' | artemisTranslate" class="hero-image" loading="lazy" />
                </div>

                <p class="description" jhiTranslate="landing.programmingExercises.description"></p>
            </div>

            <!-- Integration Cards -->
            <div class="integration-section" jhiVisibleOnScroll>
                <h3 class="subsection-title" jhiTranslate="landing.programmingExercises.integration.title"></h3>
                <p class="subsection-description" jhiTranslate="landing.programmingExercises.integration.description"></p>

                <div class="cards-grid">
                    <div class="integration-card">
                        <img src="content/images/landing/logo.png" alt="Artemis" class="card-logo" />
                        <h4 class="card-title" jhiTranslate="landing.programmingExercises.integration.cards.git.title"></h4>
                        <p class="card-description" jhiTranslate="landing.programmingExercises.integration.cards.git.description"></p>
                    </div>

                    <div class="integration-card">
                        <img src="content/images/landing/logo.png" alt="Artemis" class="card-logo" />
                        <h4 class="card-title" jhiTranslate="landing.programmingExercises.integration.cards.ci.title"></h4>
                        <p class="card-description" jhiTranslate="landing.programmingExercises.integration.cards.ci.description"></p>
                    </div>

                    <div class="integration-card">
                        <img src="content/images/landing/logo.png" alt="Artemis" class="card-logo" />
                        <h4 class="card-title" jhiTranslate="landing.programmingExercises.integration.cards.batteries.title"></h4>
                        <p class="card-description" jhiTranslate="landing.programmingExercises.integration.cards.batteries.description"></p>
                    </div>
                </div>
            </div>

            <!-- Assessment -->
            <div class="assessment-section" jhiVisibleOnScroll>
                <h3 class="subsection-title" jhiTranslate="landing.programmingExercises.assessment.title"></h3>
                <p class="subsection-description" jhiTranslate="landing.programmingExercises.assessment.description"></p>

                <div class="assessment-content">
                    <div class="assessment-logos">
                        <a href="https://pytest.org" target="_blank" rel="noopener" class="assessment-logo-icon" title="pytest">
                            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                                <path
                                    d="M14.044 0a1.8 1.8 0 0 0-.655.137L6.2 3.313a1.3 1.3 0 0 0-.752 1.163v5.4a1.34 1.34 0 0 0 .787 1.202l3.767 1.69a2.2 2.2 0 0 0 .862.18c.226.004.453-.034.667-.113l7.187-3.177a1.3 1.3 0 0 0 .752-1.163v-5.4a1.34 1.34 0 0 0-.787-1.202L14.916.203A2.2 2.2 0 0 0 14.044 0zm.062 1.508c.088-.003.186.017.287.062l3.414 1.573c.305.137.305.363 0 .5l-3.7 1.64a1.05 1.05 0 0 1-.835 0l-3.414-1.573c-.305-.137-.305-.363 0-.5l3.7-1.64c.16-.065.33-.068.548-.062zm-4.9 3.907c.088 0 .158.003.24.04l3.555 1.638c.294.137.537.486.537.787v3.302c0 .294-.243.424-.537.294l-3.555-1.638c-.294-.137-.537-.486-.537-.787V5.749c0-.228.135-.34.297-.334zm9.67.012c.164 0 .305.106.305.34v3.302c0 .294-.243.644-.537.787l-3.555 1.638c-.294.137-.537-.006-.537-.294V7.898c0-.294.243-.644.537-.787l3.555-1.638c.076-.034.152-.046.231-.046zM3.378 8.5a1.78 1.78 0 0 0-.667.137L.252 9.996A.96.96 0 0 0 0 10.656v5.399a.99.99 0 0 0 .587.893l2.834 1.274c.273.125.56.191.851.193a1.8 1.8 0 0 0 .666-.137l2.46-1.36a.96.96 0 0 0 .251-.66v-1.98l-3.4 1.54c-.294.138-.537-.005-.537-.293v-3.302c0-.294.243-.644.537-.787l3.745-1.726a.4.4 0 0 0-.083-.043l-3.636-1.637A2 2 0 0 0 3.378 8.5zm17.262.003a1.9 1.9 0 0 0-.868.2L16.14 10.33a.1.1 0 0 0-.06.044l3.674 1.652c.294.137.537.486.537.787v3.302c0 .293-.243.424-.537.294l-3.4-1.541v1.98a.96.96 0 0 0 .251.66l2.46 1.36c.209.103.436.158.666.162a1.9 1.9 0 0 0 .856-.194l2.834-1.274A.99.99 0 0 0 24 16.67v-5.399a.96.96 0 0 0-.252-.66l-2.459-1.36a1.78 1.78 0 0 0-.649-.247zm-6.592 3.408c-.164 0-.305.107-.305.34v3.297c0 .294.243.644.537.787l3.555 1.638c.294.137.537-.006.537-.294V14.58c0-.294-.243-.644-.537-.787l-3.555-1.638a.5.5 0 0 0-.232-.044zm-4.078.018a.5.5 0 0 0-.24.044l-3.555 1.638c-.294.137-.537.486-.537.787v3.302c0 .294.243.424.537.294l3.555-1.638c.294-.137.537-.486.537-.787v-3.302c0-.227-.134-.34-.297-.334zm3.088 5.67c-.076 0-.158.003-.24.044l-3.556 1.638c-.294.138-.537.487-.537.787V23.87c0 .294.243.424.537.294l3.556-1.638c.294-.137.537-.486.537-.787v-3.302c0-.228-.135-.34-.297-.334z"
                                />
                            </svg>
                        </a>
                        <a href="https://junit.org" target="_blank" rel="noopener" class="assessment-logo-icon" title="JUnit 5">
                            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                                <path
                                    d="M11.886 0c-.24 0-.476.068-.676.194L.674 7.534A1.29 1.29 0 0 0 0 8.558v7.042c0 .449.26.856.674 1.024l10.536 7.182c.41.268.944.268 1.355 0l10.758-7.34A1.29 1.29 0 0 0 24 15.441V8.399c0-.449-.261-.856-.674-1.024L12.562.035A1.3 1.3 0 0 0 11.886 0zm.003 3.07 7.817 5.174-3.088 2.1-4.729-3.168-4.73 3.169-3.087-2.101zm-4.79 6.325 4.79 3.206 4.789-3.206 3.052 2.078-7.84 5.338-7.842-5.338z"
                                />
                            </svg>
                        </a>
                        <a href="https://github.com/ls1intum/Ares" target="_blank" rel="noopener" class="assessment-logo-text" title="ARES"> ARES </a>
                    </div>
                    <div class="assessment-text">
                        <p class="assessment-scale" jhiTranslate="landing.programmingExercises.assessment.scale"></p>
                    </div>
                </div>
            </div>

            <!-- Programming Languages -->
            <div class="languages-section" jhiVisibleOnScroll>
                <p class="languages-label" jhiTranslate="landing.programmingExercises.programmingLanguages"></p>
                <div class="languages-grid">
                    @for (lang of languages; track lang.name) {
                        <a
                            [href]="lang.href"
                            [title]="lang.name"
                            class="language-icon"
                            target="_blank"
                            rel="noopener"
                            [innerHTML]="lang.icon"
                            [class]="'language-icon ' + lang.sizeClass"
                        ></a>
                    }
                </div>
            </div>
        </section>
    `,
    styles: `
        .programming-section {
            max-width: 1280px;
            margin: 0 auto;
            padding: 0 1.25rem;
        }

        .container {
            opacity: 0.5;
            transform: translateY(20px);
            transition: all 0.5s ease-in-out;
        }

        :host .container.visible,
        :host .integration-section.visible,
        :host .assessment-section.visible,
        :host .languages-section.visible {
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

        .integration-section,
        .assessment-section,
        .languages-section {
            text-align: center;
            margin-top: 4rem;
            opacity: 0.5;
            transform: translateY(20px);
            transition: all 0.5s ease-in-out;
        }

        .subsection-title {
            font-size: 1.5rem;
            line-height: 2rem;
            font-weight: 600;
            color: white;
        }

        .subsection-description {
            color: #e2e8f0;
            line-height: 1.625;
            margin-top: 1rem;
        }

        .cards-grid {
            display: grid;
            grid-template-columns: 1fr;
            gap: 1.5rem;
            margin-top: 2rem;
        }

        .integration-card {
            background: #0f172a;
            border-radius: 0.75rem;
            padding: 1.5rem;
            text-align: left;
            display: flex;
            flex-direction: column;
            gap: 0.75rem;
        }

        .card-logo {
            height: 2.5rem;
            width: auto;
            align-self: flex-start;
        }

        .card-title {
            font-size: 1.125rem;
            font-weight: 700;
            color: white;
        }

        .card-description {
            color: #cbd5e1;
            font-size: 0.875rem;
            line-height: 1.5;
        }

        .assessment-content {
            display: flex;
            flex-direction: column;
            gap: 2rem;
            margin-top: 2rem;
        }

        .assessment-logos {
            display: flex;
            gap: 4rem;
            align-items: center;
            justify-content: center;
        }

        .assessment-logo-icon {
            color: white;
            display: inline-flex;
            height: 3rem;
        }

        .assessment-logo-icon svg {
            height: 100%;
            width: auto;
        }

        .assessment-logo-text {
            color: white;
            font-size: 2.25rem;
            font-weight: 700;
            text-decoration: none;
        }

        .assessment-text {
            text-align: center;
        }

        .assessment-scale {
            color: #e2e8f0;
            line-height: 1.625;
        }

        .languages-label {
            color: #cbd5e1;
        }

        .languages-grid {
            display: flex;
            flex-wrap: wrap;
            justify-content: center;
            align-items: center;
            gap: 2rem;
            margin-top: 2.5rem;
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
            height: 2rem;
        }

        .lang-lg {
            height: 2rem;
        }

        .lang-xl {
            height: 2rem;
        }

        @media (min-width: 640px) {
            .cards-grid {
                grid-template-columns: repeat(3, 1fr);
            }
        }

        @media (min-width: 768px) {
            .languages-grid {
                gap: 5rem;
            }

            .lang-md {
                height: 3rem;
            }

            .lang-lg {
                height: 3.5rem;
            }

            .lang-xl {
                height: 4rem;
            }

            .assessment-logo-icon {
                height: 6rem;
            }

            .assessment-content {
                flex-direction: row;
                gap: 4rem;
                align-items: center;
            }

            .assessment-logos {
                flex: 1;
            }

            .assessment-text {
                flex: 1;
                text-align: left;
            }
        }

        @media (min-width: 1024px) {
            .section-title {
                font-size: 3rem;
                line-height: 1;
                letter-spacing: -0.025em;
            }

            .card-description {
                font-size: 1rem;
                line-height: 1.5rem;
            }

            .subsection-title {
                font-size: 1.75rem;
                line-height: 2.25rem;
            }
        }
    `,
})
export class LandingProgrammingExercisesComponent {
    private sanitizer = inject(DomSanitizer);

    languages: { name: string; href: string; sizeClass: string; icon: SafeHtml }[] = this.buildLanguages();

    private buildLanguages(): { name: string; href: string; sizeClass: string; icon: SafeHtml }[] {
        const raw = [
            {
                name: 'Java',
                href: 'https://www.oracle.com/java/',
                sizeClass: 'lang-md',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M11.915 0L11.7.215C9.515 2.4 7.47 6.39 6.046 10.483c-1.064 1.024-3.633 2.81-3.711 3.551c-.093.87 1.746 2.611 1.55 3.235c-.198.625-1.304 1.408-1.014 1.939c.1.188.823.011 1.277-.491a13.4 13.4 0 0 0-.017 2.14c.076.906.27 1.668.643 2.232c.372.563.956.911 1.667.911c.397 0 .727-.114 1.024-.264c.298-.149.571-.33.91-.5c.68-.34 1.634-.666 3.53-.604c1.903.062 2.872.39 3.559.704s1.15.664 1.925.664c.767 0 1.395-.336 1.807-.9c.412-.563.631-1.33.72-2.24c.06-.623.055-1.32 0-2.066c.454.45 1.117.604 1.213.424c.29-.53-.816-1.314-1.013-1.937c-.198-.624 1.642-2.366 1.549-3.236c-.08-.748-2.707-2.568-3.748-3.586C16.428 6.374 14.308 2.394 12.13.215zm.175 6.038a2.95 2.95 0 0 1 2.943 2.942a2.95 2.95 0 0 1-2.943 2.943A2.95 2.95 0 0 1 9.148 8.98a2.95 2.95 0 0 1 2.942-2.942M8.685 7.983a3.5 3.5 0 0 0-.145.997c0 1.951 1.6 3.55 3.55 3.55s3.55-1.598 3.55-3.55q-.002-.495-.132-.951q.502.143.915.336a43 43 0 0 1 2.042 5.829c.678 2.545 1.01 4.92.846 6.607c-.082.844-.29 1.51-.606 1.94c-.315.431-.713.651-1.315.651c-.593 0-.932-.27-1.673-.61c-.741-.338-1.825-.694-3.792-.758c-1.974-.064-3.073.293-3.821.669c-.375.188-.659.373-.911.5s-.466.2-.752.2c-.53 0-.876-.209-1.16-.64c-.285-.43-.474-1.101-.545-1.948c-.141-1.693.176-4.069.823-6.614a43 43 0 0 1 1.934-5.783c.348-.167.749-.31 1.192-.425m-3.382 4.362a.2.2 0 0 1 .13.031c-.166.56-.323 1.116-.463 1.665a34 34 0 0 0-.547 2.555a4 4 0 0 0-.2-.39c-.58-1.012-.914-1.642-1.16-2.08c.315-.24 1.679-1.755 2.24-1.781m13.394.01c.562.027 1.926 1.543 2.24 1.783c-.246.438-.58 1.068-1.16 2.08a4 4 0 0 0-.163.309a32 32 0 0 0-.562-2.49a41 41 0 0 0-.482-1.652a.2.2 0 0 1 .127-.03"/></svg>',
            },
            {
                name: 'Swift',
                href: 'https://developer.apple.com/swift/',
                sizeClass: 'lang-md',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="m7.508 0l-.86.002q-.362.001-.724.01q-.198.005-.395.015A9 9 0 0 0 4.348.15 5.5 5.5 0 0 0 2.85.645 5.04 5.04 0 0 0 .645 2.848c-.245.48-.4.972-.495 1.5c-.093.52-.122 1.05-.136 1.576a35 35 0 0 0-.012.724L0 7.508v8.984l.002.862q.002.36.012.722c.014.526.043 1.057.136 1.576c.095.528.25 1.02.495 1.5a5.03 5.03 0 0 0 2.205 2.203c.48.244.97.4 1.498.495c.52.093 1.05.124 1.576.138q.362.01.724.01l.86.002h8.984l.86-.002q.362 0 .724-.01a10.5 10.5 0 0 0 1.578-.138 5.3 5.3 0 0 0 1.498-.495 5.04 5.04 0 0 0 2.203-2.203c.245-.48.4-.972.495-1.5c.093-.52.124-1.05.138-1.576q.01-.361.01-.722l.002-.862V7.508l-.002-.86a34 34 0 0 0-.01-.724 10.5 10.5 0 0 0-.138-1.576 5.3 5.3 0 0 0-.495-1.5A5.04 5.04 0 0 0 21.152.645 5.3 5.3 0 0 0 19.654.15a10.5 10.5 0 0 0-1.578-.138 35 35 0 0 0-.722-.01L16.492 0zm6.035 3.41c4.114 2.47 6.545 7.162 5.549 11.131c-.024.093-.05.181-.076.272l.002.001c2.062 2.538 1.5 5.258 1.236 4.745c-1.072-2.086-3.066-1.568-4.088-1.043a7 7 0 0 1-.281.158l-.02.012-.002.002c-2.115 1.123-4.957 1.205-7.812-.022a12.57 12.57 0 0 1-5.64-4.838c.649.48 1.35.902 2.097 1.252c3.019 1.414 6.051 1.311 8.197-.002C9.651 12.73 7.101 9.67 5.146 7.191a10.6 10.6 0 0 1-1.005-1.384c2.34 2.142 6.038 4.83 7.365 5.576C8.69 8.408 6.208 4.743 6.324 4.86c4.436 4.47 8.528 6.996 8.528 6.996c.154.085.27.154.36.213q.128-.322.224-.668c.708-2.588-.09-5.548-1.893-7.992z"/></svg>',
            },
            {
                name: 'Kotlin',
                href: 'https://kotlinlang.org/',
                sizeClass: 'lang-md',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M24 24H0V0h24L12 12Z"/></svg>',
            },
            {
                name: 'Python',
                href: 'https://www.python.org/',
                sizeClass: 'lang-md',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="m14.25.18l.9.2l.73.26l.59.3l.45.32l.34.34l.25.34l.16.33l.1.3l.04.26l.02.2l-.01.13V8.5l-.05.63l-.13.55l-.21.46l-.26.38l-.3.31l-.33.25l-.35.19l-.35.14l-.33.1l-.3.07l-.26.04l-.21.02H8.77l-.69.05l-.59.14l-.5.22l-.41.27l-.33.32l-.27.35l-.2.36l-.15.37l-.1.35l-.07.32l-.04.27l-.02.21v3.06H3.17l-.21-.03l-.28-.07l-.32-.12l-.35-.18l-.36-.26l-.36-.36l-.35-.46l-.32-.59l-.28-.73l-.21-.88l-.14-1.05l-.05-1.23l.06-1.22l.16-1.04l.24-.87l.32-.71l.36-.57l.4-.44l.42-.33l.42-.24l.4-.16l.36-.1l.32-.05l.24-.01h.16l.06.01h8.16v-.83H6.18l-.01-2.75l-.02-.37l.05-.34l.11-.31l.17-.28l.25-.26l.31-.23l.38-.2l.44-.18l.51-.15l.58-.12l.64-.1l.71-.06l.77-.04l.84-.02l1.27.05zm-6.3 1.98l-.23.33l-.08.41l.08.41l.23.34l.33.22l.41.09l.41-.09l.33-.22l.23-.34l.08-.41l-.08-.41l-.23-.33l-.33-.22l-.41-.09l-.41.09zm13.09 3.95l.28.06l.32.12l.35.18l.36.27l.36.35l.35.47l.32.59l.28.73l.21.88l.14 1.04l.05 1.23l-.06 1.23l-.16 1.04l-.24.86l-.32.71l-.36.57l-.4.45l-.42.33l-.42.24l-.4.16l-.36.09l-.32.05l-.24.02l-.16-.01h-8.22v.82h5.84l.01 2.76l.02.36l-.05.34l-.11.31l-.17.29l-.25.25l-.31.24l-.38.2l-.44.17l-.51.15l-.58.13l-.64.09l-.71.07l-.77.04l-.84.01l-1.27-.04l-1.07-.14l-.9-.2l-.73-.25l-.59-.3l-.45-.33l-.34-.34l-.25-.34l-.16-.33l-.1-.3l-.04-.25l-.02-.2l.01-.13v-5.34l.05-.64l.13-.54l.21-.46l.26-.38l.3-.32l.33-.24l.35-.2l.35-.14l.33-.1l.3-.06l.26-.04l.21-.02l.13-.01h5.84l.69-.05l.59-.14l.5-.21l.41-.28l.33-.32l.27-.35l.2-.36l.15-.36l.1-.35l.07-.32l.04-.28l.02-.21V6.07h2.09l.14.01zm-6.47 14.25l-.23.33l-.08.41l.08.41l.23.33l.33.23l.41.08l.41-.08l.33-.23l.23-.33l.08-.41l-.08-.41l-.23-.33l-.33-.23l-.41-.08l-.41.08z"/></svg>',
            },
            {
                name: 'C',
                href: 'https://www.iso.org/standard/74528.html',
                sizeClass: 'lang-lg',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M16.592 9.196s-.354-3.298-3.627-3.39c-3.274-.09-4.955 2.474-4.955 6.14s1.858 6.597 5.045 6.597c3.184 0 3.538-3.665 3.538-3.665l6.104.365s.36 3.31-2.196 5.836c-2.552 2.524-5.69 2.937-7.876 2.92c-2.19-.016-5.226.035-8.16-2.97c-2.938-3.01-3.436-5.93-3.436-8.8s.556-6.67 4.047-9.55C7.444.72 9.849 0 12.254 0c10.042 0 10.717 9.26 10.717 9.26z"/></svg>',
            },
            {
                name: 'OCaml',
                href: 'https://ocaml.org/',
                sizeClass: 'lang-xl',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M12.178 21.637c-.085-.17-.187-.524-.255-.676c-.067-.135-.27-.506-.37-.625c-.22-.253-.27-.27-.338-.608c-.12-.574-.405-1.588-.76-2.296c-.187-.372-.49-.677-.761-.947c-.236-.236-.777-.624-.878-.607c-.895.169-1.166 1.046-1.587 1.739c-.237.388-.473.71-.66 1.115c-.167.371-.151.793-.439 1.115a2.95 2.95 0 0 0-.624 1.097c-.034.084-.101.929-.186 1.131l1.318-.084c1.233.085.877.557 2.787.456l3.022-.1a5.4 5.4 0 0 0-.27-.71zM20.96 1.539H3.023A3.02 3.02 0 0 0 0 4.56v6.587c.44-.152 1.047-1.08 1.25-1.3c.337-.389.405-.895.574-1.2c.389-.709.456-1.215 1.334-1.215c.406 0 .575.1.845.473c.186.253.523.743.675 1.064c.186.371.474.86.609.962c.1.068.185.136.27.17c.135.05.253-.051.354-.12c.118-.1.17-.286.287-.556c.17-.39.339-.827.44-.997c.169-.27.236-.608.422-.76c.27-.236.641-.253.743-.27c.557-.118.81.27 1.08.507c.186.168.423.49.609.91c.135.339.304.661.388.846c.068.185.237.49.338.86c.101.322.337.575.44.744c0 0 .152.406 1.03.778a8 8 0 0 0 .81.286c.39.135.76.12 1.233.068c.338 0 .524-.49.676-.878c.084-.237.185-.895.236-1.081s-.085-.32.034-.49c.135-.186.22-.203.287-.439c.17-.523 1.114-.54 1.655-.54c.456 0 .389.44 1.149.287c.439-.085.86.05 1.318.185c.388.102.76.22.98.473c.134.17.489.997.134 1.031c.033.033.067.118.118.151c-.085.322-.422.085-.625.051c-.253-.05-.44 0-.693.118c-.439.187-1.063.17-1.452.49c-.32.271-.32.861-.473 1.2c0 0-.422 1.063-1.317 1.722c-.237.17-.692.574-1.672.726c-.44.068-.86.068-1.318.05c-.22-.016-.438-.016-.658-.016c-.136 0-.575-.017-.558.034l-.05.119a.6.6 0 0 0 .033.169c.017.1.017.185.034.27c0 .185-.017.388 0 .574c.017.388.17.743.186 1.148c.017.44.236.913.456 1.267c.085.135.203.152.254.32c.067.186 0 .406.033.609c.118.794.355 1.638.71 2.364v.017c.439-.067.895-.236 1.47-.32c1.063-.153 2.532-.085 3.478-.17c2.399-.22 3.7.98 5.844.49V4.562a3.045 3.045 0 0 0-3.04-3.023m-8.951 14.187q0-.051 0 0m-6.47 2.769c.17-.372.271-.778.406-1.15c.135-.354.337-.86.693-1.046c-.05-.05-.744-.068-.929-.085a7 7 0 0 1-.608-.084a23 23 0 0 1-1.15-.236c-.22-.051-.979-.322-1.13-.39c-.39-.168-.642-.658-.93-.607c-.185.034-.37.101-.49.287c-.1.152-.134.423-.202.608c-.084.203-.22.405-.32.608c-.238.354-.626.676-.795 1.03c-.033.085-.05.169-.084.254v4.07c.202.034.405.068.624.135c1.69.456 2.095.49 3.75.304l.152-.017c.118-.27.22-1.165.304-1.435c.067-.22.153-.39.187-.591c.033-.203 0-.406-.017-.59c-.034-.491.354-.661.54-1.065z"/></svg>',
            },
            {
                name: 'Haskell',
                href: 'https://www.haskell.org/',
                sizeClass: 'lang-xl',
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M0 3.535L5.647 12 0 20.465h4.235L9.883 12 4.235 3.535zm5.647 0L11.294 12l-5.647 8.465h4.235l3.53-5.29 3.53 5.29h4.234L9.883 3.535zm8.941 4.938l1.883 2.822H24V8.473zm2.824 4.232l1.882 2.822H24v-2.822z"/></svg>',
            },
        ];
        return raw.map((lang) => ({
            ...lang,
            icon: this.sanitizer.bypassSecurityTrustHtml(lang.icon as string),
        }));
    }
}
