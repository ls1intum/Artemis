import { Component, input } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/**
 * Small standalone component that renders a "Powered by TUM Live" attribution label
 * linking back to the original watch page on tum.live.
 *
 * Spec §7: "Every embedded video keeps a visible 'Powered by TUM Live' label linking back to the original."
 */
@Component({
    selector: 'jhi-tum-live-attribution',
    imports: [ArtemisTranslatePipe],
    template: `
        <div class="tum-live-attribution">
            <a [href]="watchUrl()" target="_blank" rel="noopener noreferrer" class="tum-live-attribution__link">
                {{ 'artemisApp.gocast.player.poweredByTumLive' | artemisTranslate }}
            </a>
        </div>
    `,
    styles: [
        `
            .tum-live-attribution {
                display: flex;
                justify-content: flex-end;
                padding: 0.25rem 0.5rem;
                font-size: 0.75rem;
            }

            .tum-live-attribution__link {
                color: inherit;
                opacity: 0.7;
                text-decoration: none;

                &:hover {
                    opacity: 1;
                    text-decoration: underline;
                }
            }
        `,
    ],
})
export class TumLiveAttributionComponent {
    /** The full watch-page URL, e.g. https://tum.live/w/{slug}/{streamId} */
    watchUrl = input.required<string>();
}
