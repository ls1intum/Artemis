import { Component, input, output } from '@angular/core';

@Component({
    selector: 'p-dialog',
    template: `<ng-content />`,
})
export class PrimeNgDialogStubComponent {
    header = input<string>('');
    modal = input(false);
    visible = input(false);
    visibleChange = output<boolean>();
}
