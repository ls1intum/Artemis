import { Component, Input } from '@angular/core';
import type { ProgrammingTestStatusDetail } from 'app/detail-overview-list/detail.model';
import { RouterModule } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming/programming-exercise-participation.model';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result.component';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/programming/shared/actions/programming-exercise-instructor-trigger-build-button.component';
import { ProgrammingExerciseInstructorStatusComponent } from 'app/programming/manage/status/programming-exercise-instructor-status.component';

@Component({
    selector: 'jhi-programming-test-status-detail',
    templateUrl: 'programming-test-status-detail.component.html',
    imports: [RouterModule, TranslateDirective, UpdatingResultComponent, ProgrammingExerciseInstructorTriggerBuildButtonComponent, ProgrammingExerciseInstructorStatusComponent],
})
export class ProgrammingTestStatusDetailComponent {
    protected readonly ProgrammingExerciseParticipationType = ProgrammingExerciseParticipationType;

    @Input({ required: true }) detail: ProgrammingTestStatusDetail;
}
