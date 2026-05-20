import { Component } from '@angular/core';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-loading-indicator-overlay',
    imports: [ProgressSpinnerModule, ArtemisTranslatePipe],
    templateUrl: './loading-indicator-overlay.component.html',
    styleUrl: './loading-indicator-overlay.component.scss',
})
export class LoadingIndicatorOverlayComponent {}
