import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FEATURE_CARDS, FeatureCard } from 'app/core/landing/landing-data';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';

@Component({
    selector: 'jhi-landing-features',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe],
    styles: `
        :host {
            display: block;
        }

        .features {
            display: flex;
            flex-direction: column;
            gap: 24px;
            align-items: center;
            justify-content: center;
            padding: 80px 160px;
            background: var(--iris-secondary-background);
        }

        .features-header {
            width: 100%;
        }

        .features-title {
            font-size: 40px;
            font-weight: 700;
            color: var(--body-color);
            line-height: 1.5;
            text-align: center;
            margin: 0;
        }

        .features-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 16px;
            width: 100%;
        }

        .feature-card {
            background: var(--iris-primary-background);
            border-radius: 16px;
            padding: 40px;
            display: flex;
            flex-direction: column;
            gap: 16px;
            min-height: 440px;
            overflow: hidden;
        }

        .card-text {
            display: flex;
            flex-direction: column;
            gap: 4px;
        }

        .card-category {
            font-size: 14px;
            color: var(--text-body-secondary);
            line-height: 1.6;
            margin: 0;
        }

        .card-description {
            font-size: 18px;
            font-weight: 500;
            color: var(--body-color);
            line-height: 1.6;
            margin: 0;
        }

        .card-assets {
            flex: 1;
            max-height: 540px;
            overflow: hidden;
            display: flex;
            flex-direction: column;
            position: relative;
        }

        .card-image {
            border-radius: 8px;
            object-fit: contain;
            width: 100%;
            flex: 1;
        }

        @media (max-width: 1200px) {
            .features {
                padding: 80px 40px;
            }
        }

        @media (max-width: 768px) {
            .features {
                padding: 40px 20px;
            }

            .features-grid {
                grid-template-columns: 1fr;
            }

            .features-title {
                font-size: 28px;
            }

            .feature-card {
                height: auto;
                min-height: 360px;
                padding: 24px;
            }
        }
    `,
    template: `
        <section class="features" id="features">
            <div class="features-header">
                <h2 class="features-title">{{ 'landing.features.title' | artemisTranslate }}</h2>
            </div>
            <div class="features-grid">
                @for (card of cards; track card.categoryKey) {
                    <div class="feature-card">
                        <div class="card-text">
                            <p class="card-category">{{ card.categoryKey | artemisTranslate }}</p>
                            <p class="card-description">{{ card.descriptionKey | artemisTranslate }}</p>
                        </div>
                        <div class="card-assets">
                            <img
                                class="card-image"
                                [src]="cardImageSrc(card)"
                                [alt]="card.imageAltKey | artemisTranslate"
                                loading="lazy"
                                decoding="async"
                                width="567"
                                height="284"
                            />
                        </div>
                    </div>
                }
            </div>
        </section>
    `,
})
export class LandingFeaturesComponent {
    private themeService = inject(ThemeService);
    private isDark = computed(() => this.themeService.currentTheme() === Theme.DARK);

    cards = FEATURE_CARDS;

    cardImageSrc(card: FeatureCard): string {
        return this.isDark() && card.imageSrcDark ? card.imageSrcDark : card.imageSrc;
    }
}
