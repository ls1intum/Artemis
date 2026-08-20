import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-landing-hero',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe],
    styles: `
        :host {
            display: block;
        }

        .hero {
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 160px 80px 160px;
            gap: 80px;
        }

        .hero-top {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 32px;
        }

        .hero-section {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 8px;
        }

        .tag {
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 8px 16px;
            border-radius: 24px;
            /* Increase background opacity + use --primary-dark for text so contrast stays ≥ 4.5:1 on light bg (fixes Lighthouse contrast audit). */
            background: color-mix(in srgb, var(--primary) 18%, transparent);
            color: var(--primary-dark, var(--primary));
            font-size: 14px;
            font-weight: 500;
            line-height: 1.5;
        }

        .hero-title {
            font-size: clamp(2rem, 4vw, 3.5rem);
            font-weight: 700;
            color: var(--body-color);
            line-height: 1.5;
            text-align: center;
            max-width: 900px;
            margin: 0;
        }

        .hero-subtitle {
            font-size: 16px;
            font-weight: 400;
            color: var(--text-body-secondary);
            line-height: 1.6;
            text-align: center;
            max-width: 800px;
            margin: 0;
        }

        .hero-actions {
            display: flex;
            flex-wrap: wrap;
            justify-content: center;
            gap: 12px;
        }

        .hero-action {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            padding: 10px 20px;
            border-radius: 8px;
            border: 1px solid transparent;
            font-size: 14px;
            font-weight: 600;
            line-height: 1.6;
            text-decoration: none;
            cursor: pointer;
            transition:
                background-color 0.2s,
                border-color 0.2s,
                color 0.2s;
        }

        .hero-action-primary {
            background-color: var(--primary-dark, var(--primary));
            color: var(--white);
        }

        .hero-action-primary:hover {
            background-color: var(--primary);
            color: var(--white);
        }

        .hero-action-secondary {
            background: transparent;
            border-color: var(--iris-accent-background);
            color: var(--body-color);
        }

        .hero-action-secondary:hover {
            border-color: var(--primary);
            color: var(--primary-dark, var(--primary));
        }

        .hero-action:focus-visible {
            outline: 2px solid var(--primary-dark, var(--primary));
            outline-offset: 2px;
        }

        @media (max-width: 1024px) {
            .hero {
                padding: 120px 40px 60px;
            }
        }

        @media (max-width: 768px) {
            .hero {
                padding: 80px 20px 40px;
            }

            .hero-title {
                font-size: 2rem;
            }
        }
    `,
    template: `
        <section class="hero" id="hero">
            <div class="hero-top">
                <div class="hero-section">
                    <div class="tag">{{ 'landing.hero.tagline' | artemisTranslate }}</div>
                    <h1 class="hero-title">{{ 'landing.hero.title' | artemisTranslate }}</h1>
                    <p class="hero-subtitle">{{ 'landing.hero.subtitle' | artemisTranslate }}</p>
                </div>
                <div class="hero-actions">
                    <button type="button" class="hero-action hero-action-primary" (click)="navigateToLogin()">
                        {{ 'landing.hero.actions.getStarted' | artemisTranslate }}
                    </button>
                    <a class="hero-action hero-action-secondary" href="https://docs.artemis.tum.de" target="_blank" rel="noopener">
                        {{ 'landing.hero.actions.documentation' | artemisTranslate }}
                    </a>
                    <a class="hero-action hero-action-secondary" href="https://docs.artemis.tum.de/about" target="_blank" rel="noopener">
                        {{ 'landing.hero.actions.about' | artemisTranslate }}
                    </a>
                    <a class="hero-action hero-action-secondary" href="https://github.com/ls1intum/Artemis" target="_blank" rel="noopener">
                        {{ 'landing.hero.actions.github' | artemisTranslate }}
                    </a>
                </div>
            </div>
        </section>
    `,
})
export class LandingHeroComponent {
    private router = inject(Router);

    navigateToLogin(): void {
        void this.router.navigateByUrl('/sign-in');
    }
}
