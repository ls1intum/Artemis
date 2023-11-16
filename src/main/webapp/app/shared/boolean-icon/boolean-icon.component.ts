import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-boolean-icon',
    templateUrl: './boolean-icon.component.html',
})
export class BooleanIconComponent {
    @Input()
    iconBoolean?: boolean;
}
