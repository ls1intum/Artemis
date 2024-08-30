import { Component, Input } from '@angular/core';
import type { ProgrammingTestStatusDetail } from 'app/detail-overview-list/detail.model';
import { RouterModule } from '@angular/router';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming-exercise-participation.model';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-programming-test-status-detail',
    templateUrl: 'programming-test-status-detail.component.html',
    standalone: true,
    imports: [RouterModule, ArtemisProgrammingExerciseActionsModule, SubmissionResultStatusModule, ArtemisProgrammingExerciseStatusModule, TranslateDirective],
})
export class ProgrammingTestStatusDetailComponent {
    protected readonly ProgrammingExerciseParticipationType = ProgrammingExerciseParticipationType;

    @Input() detail: ProgrammingTestStatusDetail;
}
