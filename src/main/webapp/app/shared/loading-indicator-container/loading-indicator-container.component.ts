import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-loading-indicator-container',
    templateUrl: './loading-indicator-container.component.html',
    styleUrls: ['./loading-indicator-container.component.scss'],
})
export class LoadingIndicatorContainerComponent {
    @Input() isLoading = false;
}
