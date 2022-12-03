import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-loading-indicator-container',
    templateUrl: './loading-indicator-container.component.html',
    styles: [],
})
export class LoadingIndicatorContainerComponent {
    @Input() isLoading = false;
}
