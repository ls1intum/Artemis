import { Component, Input } from '@angular/core';

@Component({ selector: 'jhi-loading-indicator-container', template: '<ng-content />', standalone: false })
export class LoadingIndicatorContainerStubComponent {
    @Input()
    isLoading = false;
}
