import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Prerequisite } from 'app/entities/prerequisite.model';

@Component({
    selector: 'jhi-prerequisite-form',
    template: '',
    standalone: true,
})
export class PrerequisiteFormStubComponent {
    @Input()
    prerequisite?: Prerequisite;
    @Input()
    courseId: number;

    @Output()
    onCancel: EventEmitter<any> = new EventEmitter<any>();
    @Output()
    onSubmit: EventEmitter<Prerequisite> = new EventEmitter<Prerequisite>();
}
