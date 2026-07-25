import { Component } from '@angular/core';
import { faClock, faGlobe } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent, FaStackComponent, FaStackItemSizeDirective } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiTooltipDirective } from 'app/shared-ui/tum-ui/tooltip/tum-ui-tooltip.directive';

@Component({
    selector: 'jhi-time-zone-warning',
    templateUrl: './time-zone-warning.component.html',
    styleUrls: ['./time-zone-warning.component.scss'],
    imports: [FaStackComponent, FaIconComponent, FaStackItemSizeDirective, ArtemisTranslatePipe, TumUiTooltipDirective],
})
export class TimeZoneWarningComponent {
    protected readonly faGlobe = faGlobe;
    protected readonly faClock = faClock;

    /**
     * Get the current time zone of the user / browser.
     */
    get currentTimeZone(): string {
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
    }
}
