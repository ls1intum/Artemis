import { Component, EventEmitter, Input, NgModule, Output } from '@angular/core';

@Component({
    // eslint-disable-next-line @angular-eslint/component-selector
    selector: 'ngb-alert',
    exportAs: 'ngbAlert',
    template: '<ng-content />',
})
class NgbAlertMockComponent {
    @Input() animation: boolean;
    @Input() dismissible: boolean;
    @Input() type: string;
    @Output() closed = new EventEmitter<void>();
}

@NgModule({
    declarations: [NgbAlertMockComponent],
    exports: [NgbAlertMockComponent],
})
export class NgbAlertsMocksModule {}
