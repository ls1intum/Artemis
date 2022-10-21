import { Component, Input } from '@angular/core';

@Component({ selector: 'jhi-loading-indicator-container', template: '<ng-content></ng-content>' })
export class LoadingIndicatorContainerStubComponent {
    @Input()
    isLoading = false;
}
