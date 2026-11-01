import { Component } from '@angular/core';
import { TumAetUiProgressSpinnerComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-loading-indicator-overlay',
    imports: [TumAetUiProgressSpinnerComponent, ArtemisTranslatePipe],
    templateUrl: './loading-indicator-overlay.component.html',
    styleUrl: './loading-indicator-overlay.component.scss',
})
export class LoadingIndicatorOverlayComponent {}
