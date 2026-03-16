import { Component } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { VisibleOnScrollDirective } from './visible-on-scroll.directive';

@Component({
    selector: 'jhi-landing-hero',
    standalone: true,
    imports: [TranslateDirective, ArtemisTranslatePipe, VisibleOnScrollDirective],
    template: `
        <section class="hero-section">
            <main class="hero-main" jhiVisibleOnScroll [threshold]="0.05">
                <div>
                    <h1 class="hero-title">
                        <span jhiTranslate="landing.hero.title"></span>
                        <br />
                        <span jhiTranslate="landing.hero.title2"></span>
                    </h1>
                    <p class="hero-description" jhiTranslate="landing.hero.description"></p>
                    <p class="hero-subtitle" jhiTranslate="landing.hero.subtitle"></p>

                    <div class="hero-actions">
                        <a href="https://github.com/ls1intum/Artemis" target="_blank" rel="noopener noreferrer" class="btn-primary-landing">
                            <!-- GitHub icon -->
                            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="currentColor" class="gh-icon">
                                <path
                                    d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"
                                />
                            </svg>
                            <span jhiTranslate="landing.hero.github"></span>
                        </a>
                        <a href="mailto:artemis&#64;xcit.tum.de" class="btn-secondary-landing">
                            <span jhiTranslate="landing.hero.getInTouch"></span>
                        </a>
                    </div>
                </div>

                <div class="hero-screenshot">
                    <img src="content/images/landing/screen.png" [alt]="'landing.hero.screenshotAlt' | artemisTranslate" class="screenshot-img" />
                </div>
            </main>

            <div class="university-logos" jhiVisibleOnScroll [threshold]="0.05">
                <h2 class="used-by" jhiTranslate="landing.hero.usedBy"></h2>
                <div class="logos-grid">
                    @for (logo of universityLogos; track logo.name) {
                        <a [href]="logo.href" [title]="logo.name" class="logo-item" target="_blank" rel="noopener noreferrer">
                            <img [src]="'content/images/landing/user-logos/' + logo.file" [alt]="logo.name" class="university-logo" loading="lazy" />
                        </a>
                    }
                </div>
            </div>
        </section>
    `,
    styles: `
        .hero-section {
            max-width: 1280px;
            margin: 0 auto;
            padding: 0 1.25rem;
        }

        /* grid place-items-center pt-16 pb-8 md:pt-12 md:pb-24 text-center */
        .hero-main {
            display: grid;
            place-items: center;
            padding-top: 4rem;
            padding-bottom: 2rem;
            text-align: center;
            opacity: 0;
            transform: translateY(20px);
            transition:
                opacity 0.8s ease,
                transform 0.8s ease;
        }

        :host .hero-main.visible {
            opacity: 1;
            transform: translateY(0);
        }

        /* text-5xl lg:text-6xl xl:text-7xl font-bold lg:tracking-tight xl:tracking-tighter */
        .hero-title {
            font-size: 3rem;
            font-weight: 700;
            line-height: 1;
            margin-bottom: 0;
            --hcol: rgb(255, 255, 255);
            --hcolt: rgb(255 255 255 / 0.38);
            background: radial-gradient(circle at top, var(--hcol) 30%, var(--hcolt));
            box-decoration-break: clone;
            -webkit-background-clip: text;
            background-clip: text;
            -webkit-text-fill-color: transparent;
            padding-bottom: 4px;
        }

        /* text-lg mt-4 text-slate-300 */
        .hero-description {
            color: #cbd5e1;
            font-size: 1.125rem;
            line-height: 1.75rem;
            margin-top: 1rem;
        }

        /* text-lg mt-8 text-slate-300 text-center */
        .hero-subtitle {
            color: #cbd5e1;
            font-size: 1.125rem;
            line-height: 1.75rem;
            margin-top: 2rem;
            text-align: center;
        }

        /* mt-6 flex justify-center gap-3 */
        .hero-actions {
            display: flex;
            justify-content: center;
            gap: 0.75rem;
            flex-wrap: wrap;
            margin-top: 1.5rem;
        }

        /* Link primary: px-5 py-2.5 rounded border-2 border-transparent bg-white text-black shadow-lg */
        .btn-primary-landing {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 0.25rem;
            background: white;
            color: black;
            padding: 0.625rem 1.25rem;
            border-radius: 0.25rem;
            border: 2px solid transparent;
            font-weight: 400;
            text-decoration: none;
            text-align: center;
            box-shadow:
                0 10px 15px -3px rgb(0 0 0 / 0.1),
                0 4px 6px -4px rgb(0 0 0 / 0.1);
            transition:
                background 0.15s,
                color 0.15s;

            &:hover {
                background: #d1d5db;
            }
        }

        .gh-icon {
            margin-inline-end: 0.5rem;
        }

        /* Link inverted: px-5 py-2.5 rounded border-2 border-transparent bg-slate-800 text-white shadow-lg */
        .btn-secondary-landing {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 0.25rem;
            background: #1e293b;
            color: white;
            padding: 0.625rem 1.25rem;
            border-radius: 0.25rem;
            border: 2px solid transparent;
            font-weight: 400;
            text-decoration: none;
            text-align: center;
            box-shadow:
                0 10px 15px -3px rgb(0 0 0 / 0.1),
                0 4px 6px -4px rgb(0 0 0 / 0.1);
            transition:
                background 0.15s,
                color 0.15s;

            &:hover {
                background: #111827;
            }
        }

        /* py-6 block mt-12 */
        .hero-screenshot {
            padding: 1.5rem 0;
            margin-top: 3rem;
            display: block;
        }

        .screenshot-img {
            width: 100%;
            display: block;
            border-radius: 5px;
            box-shadow:
                0 0 0 1px rgba(0, 0, 0, 0.05),
                0 4px 24px rgba(0, 0, 0, 0.09);
        }

        /* mt-12 */
        .university-logos {
            margin-top: 3rem;
            opacity: 0;
            transform: translateY(20px);
            transition:
                opacity 0.8s ease 0.2s,
                transform 0.8s ease 0.2s;
        }

        :host .university-logos.visible {
            opacity: 1;
            transform: translateY(0);
        }

        /* text-center text-slate-300 text-xl font-semibold */
        .used-by {
            color: #cbd5e1;
            font-size: 1.25rem;
            font-weight: 600;
            line-height: 1.75rem;
            text-align: center;
        }

        /* flex gap-8 md:gap-20 items-center justify-center mt-10 flex-wrap pb-24 */
        .logos-grid {
            display: flex;
            flex-wrap: wrap;
            justify-content: center;
            align-items: center;
            gap: 2rem;
            margin-top: 2.5rem;
            padding-bottom: 6rem;
        }

        .logo-item {
            display: inline-flex;
            align-items: center;
            height: 40px;
            max-height: 50px;
            text-decoration: none;
        }

        .university-logo {
            height: 100%;
            width: auto;
            max-width: 200px;
            object-fit: contain;
        }

        @media (min-width: 768px) {
            .hero-main {
                padding-top: 3rem;
                padding-bottom: 6rem;
            }

            .logos-grid {
                gap: 5rem;
            }
        }

        @media (min-width: 1024px) {
            .hero-title {
                font-size: 3.75rem;
                letter-spacing: -0.025em;
            }
        }

        @media (min-width: 1280px) {
            .hero-title {
                font-size: 4.5rem;
                letter-spacing: -0.05em;
            }
        }

        @media (prefers-reduced-motion: reduce) {
            .hero-main,
            .university-logos {
                transition: none;
                opacity: 1;
                transform: none;
            }
        }
    `,
})
export class LandingHeroComponent {
    universityLogos = [
        { name: 'Technical University of Munich', file: 'tum.png', href: 'https://www.tum.de/' },
        { name: 'Hochschule München', file: 'hm.png', href: 'https://www.hm.edu/' },
        { name: 'University of Passau', file: 'uni_1200dpi_sw_gross_Grau_Weiss.png', href: 'https://www.uni-passau.de/' },
        { name: 'University of Stuttgart', file: 'unistuttgart_logo_englisch_cmyk_invertiert.png', href: 'https://www.uni-stuttgart.de/' },
        { name: 'Karlsruhe Institute of Technology', file: 'KIT-Logo.png', href: 'https://www.kit.edu/' },
        { name: 'TU Dresden', file: 'dresden.png', href: 'https://tu-dresden.de/' },
        { name: 'TU Wien', file: 'technische-universitat-wien-logo-E7B527B95B-seeklogo.com.png', href: 'https://www.tuwien.at/' },
        { name: 'AAU Klagenfurt', file: 'aau-logo-300x110-300x110without-backgroung-white-1.png', href: 'https://www.aau.at/' },
        { name: 'JKU Linz', file: 'jku.png', href: 'https://www.jku.at/' },
        { name: 'University of Salzburg', file: 'uni-sbg-logo-white.png', href: 'https://www.uni-salzburg.at/' },
        { name: 'University of Innsbruck', file: 'logo-uibk.svg', href: 'https://www.uibk.ac.at/' },
        { name: 'Maria-Theresia-Gymnasium', file: 'maria-theresia.png', href: 'https://mtg.musin.de/' },
        { name: 'Hochschule Heilbronn', file: 'Hnn_logo.svg.png', href: 'https://www.hs-heilbronn.de/' },
    ];
}
