import { Component, EventEmitter, Output } from '@angular/core';

@Component({
    selector: 'jhi-unit-creation-card',
    templateUrl: './unit-creation-card.component.html',
    styleUrls: ['./unit-creation-card.component.scss'],
})
export class UnitCreationCardComponent {
    @Output()
    createTextUnit: EventEmitter<any> = new EventEmitter<any>();

    @Output()
    createExerciseUnit: EventEmitter<any> = new EventEmitter<any>();

    @Output()
    createVideoUnit: EventEmitter<any> = new EventEmitter<any>();

    @Output()
    createAttachmentUnit: EventEmitter<any> = new EventEmitter<any>();
}
