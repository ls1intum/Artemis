import { Component } from '@angular/core';

import { Input } from '@angular/core';

@Component({ selector: 'jhi-loading-indicator-container', template: '<ng-content />' })
export class LoadingIndicatorContainerStubComponent {
    @Input()
    isLoading = false;
}
