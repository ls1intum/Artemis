import { Component, input } from '@angular/core';
import { TumAetUiProgressSpinnerComponent } from '@tumaet/ui-angular/progress-spinner';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-loading-indicator-container',
    templateUrl: './loading-indicator-container.component.html',
    styleUrls: ['./loading-indicator-container.component.css'],
    imports: [TumAetUiProgressSpinnerComponent, ArtemisTranslatePipe],
})
export class LoadingIndicatorContainerComponent {
    isLoading = input<boolean>(false);
}
