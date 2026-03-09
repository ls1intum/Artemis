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
                                    d="M2.615 0v.887h3.84V0zm5.022 0v.887h3.842V0zm4.957 0v.887h3.841V0zm4.935 0v.887h3.842V0zM2.447 1.895a.935.935 0 0 0-.935.935c0 .517.418.938.935.938h19.106c.517 0 .935-.42.935-.938a.935.935 0 0 0-.935-.936zm.168 2.847V24h3.84V4.742zm5.022 0v15.801h3.842v-15.8zm4.957 0v10.549h3.85V4.742zm4.935 0v6.494h3.842V4.742z"
                                />
                            </svg>
                        </a>
                        <a href="https://junit.org" target="_blank" rel="noopener" class="assessment-logo-icon" title="JUnit 5">
                            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                                <path
                                    d="M11.886 9.769q2.47 0 3.912 1.307q1.442 1.308 1.442 3.566q0 2.616-1.643 4.09q-1.632 1.465-4.65 1.465q-2.739 0-4.303-.883v-2.38a8 8 0 0 0 2.079.793q1.173.28 2.18.28q1.776 0 2.704-.794t.928-2.325q0-2.928-3.733-2.929q-.525 0-1.297.112q-.771.1-1.352.235l-1.174-.693l.626-7.98H16.1v2.335H9.919l-.37 4.046q.393-.066.95-.156q.57-.09 1.387-.09zM12 0C5.373 0 0 5.373 0 12a12 12 0 0 0 6.65 10.738v-3.675h.138c.01.004 4.86 2.466 8.021 0c3.163-2.468 1.62-5.785 1.08-6.557c-.54-.771-3.317-2.083-5.708-1.851c-2.391.231-2.391.308-2.391.308l.617-7.096l7.687-.074V.744A12 12 0 0 0 11.999 0zm4.095.744v3.049l-7.688.074l-.617 7.096s0-.077 2.391-.308c2.392-.232 5.169 1.08 5.708 1.851c.54.772 2.083 4.089-1.08 6.557c-3.16 2.467-8.013.004-8.02 0h-.14v3.675A12 12 0 0 0 12 24c6.628 0 12-5.373 12-12A12.01 12.01 0 0 0 16.35.83a9 9 0 0 0-.255-.086M6.299 22.556"
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
                icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M8.851 18.56s-.917.534.653.714c1.902.218 2.874.187 4.969-.211 0 0 .552.346 1.321.646-4.699 2.013-10.633-.118-6.943-1.149M8.276 15.933s-1.028.761.542.924c2.032.209 3.636.227 6.413-.308 0 0 .384.389.987.602-5.679 1.661-12.007.13-7.942-1.218M13.116 11.475c1.158 1.333-.304 2.533-.304 2.533s2.939-1.518 1.589-3.418c-1.261-1.772-2.228-2.652 3.007-5.688 0-.001-8.216 2.051-4.292 6.573M19.33 20.504s.679.559-.747.991c-2.712.822-11.288 1.069-13.669.033-.856-.373.75-.89 1.254-.998.527-.114.828-.093.828-.093-.953-.671-6.156 1.317-2.643 1.887 9.58 1.553 17.462-.7 14.977-1.82M9.292 13.21s-4.362 1.036-1.544 1.412c1.189.159 3.561.123 5.77-.062 1.806-.152 3.618-.477 3.618-.477s-.637.272-1.098.587c-4.429 1.165-12.986.623-10.522-.568 2.082-1.006 3.776-.892 3.776-.892M17.116 17.584c4.503-2.34 2.421-4.589.968-4.285-.355.074-.515.138-.515.138s.132-.207.385-.297c2.875-1.011 5.086 2.981-.928 4.562 0-.001.07-.062.09-.118M14.401 0s2.494 2.494-2.365 6.33c-3.896 3.077-.888 4.832-.001 6.836-2.274-2.053-3.943-3.858-2.824-5.539 1.644-2.469 6.197-3.665 5.19-7.627M9.734 23.924c4.322.277 10.959-.153 11.116-2.198 0 0-.302.775-3.572 1.391-3.688.694-8.239.613-10.937.168 0-.001.553.457 3.393.639"/></svg>',
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
