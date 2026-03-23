import { Component } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FEATURE_CARDS } from 'app/core/landing/landing-data';

@Component({
    selector: 'jhi-landing-features',
    standalone: true,
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
            font-size: 16px;
            font-weight: 400;
            color: var(--text-body-secondary);
            line-height: 1.6;
            margin: 0;
        }

        .card-description {
            font-size: 20px;
            font-weight: 500;
            color: var(--body-color);
            line-height: 1.6;
            margin: 0;
        }

        .card-assets {
            flex: 1;
            max-height: 400px;
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

        .card-image-no-border {
            border-radius: 8px;
            object-fit: cover;
            width: 100%;
            flex: 1;
        }

        .card-assets-dual {
            flex: 1;
            min-height: 200px;
            overflow: hidden;
            position: relative;
        }

        .card-assets-dual .primary-image {
            border-radius: 8px;
            object-fit: cover;
            height: 100%;
            position: absolute;
            left: 0;
            top: 0;
        }

        .card-assets-dual .secondary-image {
            border-radius: 8px;
            object-fit: cover;
            height: 90%;
            position: absolute;
            right: 0;
            top: 10%;
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
                        @if (card.secondaryImageSrc) {
                            <div class="card-assets-dual">
                                <img class="primary-image" [src]="card.imageSrc" [alt]="card.imageAlt" />
                                <img class="secondary-image" [src]="card.secondaryImageSrc" [alt]="card.imageAlt" />
                            </div>
                        } @else {
                            <div class="card-assets">
                                <img class="card-image" [src]="card.imageSrc" [alt]="card.imageAlt" />
                            </div>
                        }
                    </div>
                }
            </div>
        </section>
    `,
})
export class LandingFeaturesComponent {
    cards = FEATURE_CARDS;
}
