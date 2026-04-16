import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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
            gap: 24px;
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
            </div>
        </section>
    `,
})
export class LandingHeroComponent {}
