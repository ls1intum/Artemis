import { Component, EventEmitter, Output } from '@angular/core';

@Component({
    selector: 'jhi-module-creation-card',
    templateUrl: './module-creation-card.component.html',
    styleUrls: ['./module-creation-card.component.scss'],
})
export class ModuleCreationCardComponent {
    @Output()
    createHTMLModule: EventEmitter<any> = new EventEmitter<any>();

    @Output()
    createExerciseModule: EventEmitter<any> = new EventEmitter<any>();

    @Output()
    createVideoModule: EventEmitter<any> = new EventEmitter<any>();

    @Output()
    createAttachmentModule: EventEmitter<any> = new EventEmitter<any>();
}
