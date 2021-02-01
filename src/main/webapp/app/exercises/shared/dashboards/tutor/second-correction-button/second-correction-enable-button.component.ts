import { Component, Input, Output, EventEmitter } from '@angular/core';

@Component({
    selector: 'jhi-second-correction-enable-button',
    templateUrl: './second-correction-enable-button.component.html',
    styles: ['div { cursor: pointer; }'],
})
export class SecondCorrectionEnableButtonComponent {
    @Input() secondCorrectionEnabled: boolean;
    @Input() toggelingSecondCorrectionButton: boolean;

    @Output() ngModelChange = new EventEmitter();

    triggerSecondCorrectionButton() {
        this.ngModelChange.emit();
    }
}
