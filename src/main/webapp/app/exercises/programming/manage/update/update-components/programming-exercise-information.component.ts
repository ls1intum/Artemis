import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { ProgrammingExercise, ProjectType } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { isMessagingEnabled } from 'app/entities/course.model';
import { getCourseFromExercise, requiresChannelName } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-programming-exercise-info',
    templateUrl: './programming-exercise-information.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseInformationComponent implements OnChanges {
    @Input() isImportFromExistingExercise: boolean;
    @Input() isExamMode: boolean;
    @Input() isEdit: boolean;
    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

    hideChannelNameInput = false;

    protected readonly ProjectType = ProjectType;
    protected readonly isMessagingEnabled = isMessagingEnabled;
    protected readonly getCourseFromExercise = getCourseFromExercise;

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.programmingExercise || changes.isImportFromExistingExercise || changes.isExamMode) {
            this.hideChannelNameInput = !requiresChannelName(this.programmingExercise, this.isExamMode, !this.isEdit);
        }
    }
}
