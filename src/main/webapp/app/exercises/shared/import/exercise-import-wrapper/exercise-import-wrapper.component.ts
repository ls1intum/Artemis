import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';

@Component({
    selector: 'jhi-exercise-import-wrapper',
    templateUrl: './exercise-import-wrapper.component.html',
})
export class ExerciseImportWrapperComponent implements OnInit {
    readonly ExerciseType = ExerciseType;

    @Input()
    exerciseType: ExerciseType;
    titleKey: string;
    @Input()
    programmingLanguage?: ProgrammingLanguage;

    constructor(private activeModal: NgbActiveModal) {}

    ngOnInit(): void {
        if (this.programmingLanguage) {
            this.titleKey = 'artemisApp.programmingExercise.configureGrading.categories.importLabel';
        } else if (this.exerciseType === ExerciseType.FILE_UPLOAD) {
            this.titleKey = 'artemisApp.fileUploadExercise.home.importLabel';
        } else {
            this.titleKey = `artemisApp.${this.exerciseType}Exercise.home.importLabel`;
        }
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }
}
