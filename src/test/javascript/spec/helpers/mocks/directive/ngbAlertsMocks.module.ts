import { Component, EventEmitter, Input, NgModule, Output } from '@angular/core';

@Component({
    selector: 'ngb-alert',
    exportAs: 'ngbAlert',
    template: '<ng-content></ng-content>',
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
