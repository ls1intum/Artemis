import { Component, Input, OnInit, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseType } from 'app/exercise/entities/exercise.model';
import { ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExerciseImportTabsComponent } from '../exercise-import-tabs.component';
import { ExerciseImportComponent } from '../exercise-import.component';

@Component({
    selector: 'jhi-exercise-import-wrapper',
    templateUrl: './exercise-import-wrapper.component.html',
    imports: [FormsModule, TranslateDirective, ExerciseImportTabsComponent, ExerciseImportComponent],
})
export class ExerciseImportWrapperComponent implements OnInit {
    private activeModal = inject(NgbActiveModal);

    readonly ExerciseType = ExerciseType;

    @Input()
    exerciseType: ExerciseType;
    titleKey: string;
    @Input()
    programmingLanguage?: ProgrammingLanguage;

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
