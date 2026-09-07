import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowUpRightFromSquare } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { COMMUNITY_LINKS } from 'app/core/landing/landing-data';

@Component({
    selector: 'jhi-landing-community',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, FaIconComponent],
    styles: `
        :host {
            display: block;
        }

        .community {
            display: grid;
            grid-template-columns: 400px 1fr;
            gap: 40px;
            align-items: start;
            padding: 80px 160px;
        }

        .community-header {
            display: flex;
            flex-direction: column;
            gap: 8px;
        }

        .community-label {
            font-size: 12px;
            font-weight: 400;
            color: var(--text-body-secondary);
            text-transform: uppercase;
            line-height: 1.6;
            letter-spacing: 0.05em;
        }

        .community-title {
            font-size: 32px;
            font-weight: 700;
            color: var(--body-color);
            line-height: 1.4;
            margin: 0;
        }

        .community-description {
            font-size: 16px;
            font-weight: 400;
            color: var(--text-body-secondary);
            line-height: 1.6;
            margin: 0;
        }

        .community-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 16px;
            margin: 0;
            padding: 0;
            list-style: none;
        }

        .community-card {
            display: flex;
            flex-direction: column;
            gap: 4px;
            height: 100%;
            padding: 24px;
            border-radius: 16px;
            border: 0.75px solid var(--iris-accent-background);
            background: var(--iris-secondary-background);
            text-decoration: none;
            transition: border-color 0.2s;
        }

        .community-card:hover {
            border-color: var(--primary);
            text-decoration: none;
        }

        .community-card:focus-visible {
            outline: 2px solid var(--primary-dark, var(--primary));
            outline-offset: 2px;
        }

        .community-card-title {
            display: flex;
            align-items: center;
            gap: 8px;
            font-size: 18px;
            font-weight: 600;
            color: var(--body-color);
            line-height: 1.4;
            margin: 0;
        }

        .community-card-icon {
            font-size: 12px;
            color: var(--text-body-secondary);
        }

        .community-card-description {
            font-size: 15px;
            font-weight: 400;
            color: var(--text-body-secondary);
            line-height: 1.6;
            margin: 0;
        }

        @media (max-width: 1200px) {
            .community {
                grid-template-columns: 1fr;
                padding: 80px 40px;
            }
        }

        @media (max-width: 768px) {
            .community {
                padding: 40px 20px;
                gap: 24px;
            }

            .community-title {
                font-size: 28px;
            }

            .community-grid {
                grid-template-columns: 1fr;
            }
        }

        @media (prefers-reduced-motion: reduce) {
            .community-card {
                transition: none;
            }
        }
    `,
    template: `
        <section class="community" id="community" aria-labelledby="community-title">
            <div class="community-header">
                <span class="community-label">{{ 'landing.community.label' | artemisTranslate }}</span>
                <h2 class="community-title" id="community-title">{{ 'landing.community.title' | artemisTranslate }}</h2>
                <p class="community-description">{{ 'landing.community.description' | artemisTranslate }}</p>
            </div>
            <ul class="community-grid">
                @for (link of links; track link.href) {
                    <li>
                        <a class="community-card" [href]="link.href" target="_blank" rel="noopener">
                            <h3 class="community-card-title">
                                {{ link.titleKey | artemisTranslate }}
                                <fa-icon class="community-card-icon" [icon]="faArrowUpRightFromSquare" />
                                <span class="visually-hidden">{{ 'landing.opensInNewTab' | artemisTranslate }}</span>
                            </h3>
                            <p class="community-card-description">{{ link.descriptionKey | artemisTranslate }}</p>
                        </a>
                    </li>
                }
            </ul>
        </section>
    `,
})
export class LandingCommunityComponent {
    protected readonly faArrowUpRightFromSquare = faArrowUpRightFromSquare;

    links = COMMUNITY_LINKS;
}
