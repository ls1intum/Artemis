import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';

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
        } else {
            this.titleKey =
                this.exerciseType === ExerciseType.FILE_UPLOAD ? `artemisApp.fileUploadExercise.home.importLabel` : `artemisApp.${this.exerciseType}Exercise.home.importLabel`;
        }
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }
}
