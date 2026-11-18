import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FEATURE_CARDS, FeatureCard, FeatureCardDownloadLink } from 'app/core/landing/landing-data';
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

        .feature-text {
            display: flex;
            flex-direction: column;
            gap: 4px;
        }

        .feature-category {
            font-size: 14px;
            color: var(--text-body-secondary);
            line-height: 1.6;
            margin: 0;
        }

        .feature-description {
            font-size: 18px;
            font-weight: 500;
            color: var(--body-color);
            line-height: 1.6;
            margin: 0;
        }

        .feature-assets {
            flex: 1;
            max-height: 540px;
            overflow: hidden;
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
            position: relative;
        }

        .feature-image {
            border-radius: 8px;
            object-fit: contain;
            width: 100%;
            flex: 1;
        }

        .feature-downloads {
            display: flex;
            flex-wrap: wrap;
            gap: 12px;
            justify-content: flex-end;
        }

        .feature-download {
            display: inline-flex;
            line-height: 0;
            transition:
                transform 150ms ease,
                opacity 150ms ease;
        }

        .feature-download:hover {
            transform: translateY(-1px);
        }

        .feature-download-badge {
            height: 44px;
            width: auto;
            display: block;
        }

        .feature-download.is-disabled {
            opacity: 0.5;
            pointer-events: none;
            cursor: not-allowed;
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
                        <div class="feature-text">
                            <p class="feature-category">{{ card.categoryKey | artemisTranslate }}</p>
                            <p class="feature-description">{{ card.descriptionKey | artemisTranslate }}</p>
                        </div>
                        <div class="feature-assets">
                            <img
                                class="feature-image"
                                [src]="cardImageSrc(card)"
                                [alt]="card.imageAltKey | artemisTranslate"
                                loading="lazy"
                                decoding="async"
                                width="567"
                                height="284"
                            />
                            @if (card.downloadLinks?.length) {
                                <div class="feature-downloads">
                                    @for (link of card.downloadLinks; track link.href) {
                                        <a
                                            class="feature-download"
                                            [class.is-disabled]="link.disabled"
                                            [href]="link.href"
                                            [attr.aria-disabled]="link.disabled ? 'true' : null"
                                            [attr.tabindex]="link.disabled ? -1 : null"
                                            target="_blank"
                                            rel="noopener noreferrer"
                                        >
                                            <img
                                                class="feature-download-badge"
                                                [src]="downloadBadgeSrc(link)"
                                                [alt]="link.altKey | artemisTranslate"
                                                loading="lazy"
                                                decoding="async"
                                            />
                                        </a>
                                    }
                                </div>
                            }
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

    downloadBadgeSrc(link: FeatureCardDownloadLink): string {
        return this.isDark() ? link.badgeSrcDark : link.badgeSrc;
    }
}
