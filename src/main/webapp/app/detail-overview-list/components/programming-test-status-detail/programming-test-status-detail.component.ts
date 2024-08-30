import { Component, Input } from '@angular/core';
import type { ProgrammingTestStatusDetail } from 'app/detail-overview-list/detail.model';
import { NoDataComponent } from 'app/shared/no-data-component';
import { RouterModule } from '@angular/router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming-exercise-participation.model';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';

@Component({
    selector: 'jhi-programming-test-status-detail',
    templateUrl: 'programming-test-status-detail.component.html',
    standalone: true,
    imports: [
        NoDataComponent,
        RouterModule,
        ArtemisSharedComponentModule,
        ArtemisProgrammingExerciseActionsModule,
        SubmissionResultStatusModule,
        ArtemisProgrammingExerciseStatusModule,
    ],
})
export class ProgrammingTestStatusDetailComponent {
    protected readonly ProgrammingExerciseParticipationType = ProgrammingExerciseParticipationType;

    @Input() detail: ProgrammingTestStatusDetail;
}
