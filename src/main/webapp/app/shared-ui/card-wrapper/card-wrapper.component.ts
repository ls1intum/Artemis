import { Component, input } from '@angular/core';
import { TumAetUiCardComponent } from '@tumaet/ui-angular/card';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-card-wrapper',
    templateUrl: './card-wrapper.component.html',
    imports: [TumAetUiCardComponent, ArtemisTranslatePipe],
})
export class CardWrapperComponent {
    title = input.required<string>();
    /**
     * The card is currently used for the quick actions and the control center. For both it makes sense to limit the width of the card to avoid unnecessary whitespace
     */
    private readonly maxWidthValue = '50rem';
    /**
     * The card is currently used for the quick actions and the control center. For both it makes sense to limit the height of the card to avoid disturbing layouts
     * With the currently used height of 90%, both components fit look good on the screen
     */
    private readonly minHeightValue = '90%';
    maxWidth = input<string>(this.maxWidthValue);
    minHeight = input<string>(this.minHeightValue);
}
