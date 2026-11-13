import { ProgrammingExerciseParticipationActionsComponent } from 'app/exercise/participation/programming-exercise-participation-actions/programming-exercise-participation-actions.component';
import { StudentExerciseActionsDirective } from 'app/exercise/participation/student-exercise-actions.directive';
import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ExternalCloningService } from 'app/programming/shared/services/external-cloning.service';
import { InitializationState, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

import { faEye, faFolderOpen, faPlayCircle, faRedo, faUsers } from '@fortawesome/free-solid-svg-icons';

import { NgTemplateOutlet } from '@angular/common';
import { ExerciseActionButtonComponent } from 'app/shared-ui/components/buttons/exercise-action-button/exercise-action-button.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { CodeButtonComponent } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { RequestFeedbackButtonComponent } from '../request-feedback-button/request-feedback-button.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

import { OpenCodeEditorButtonComponent } from 'app/course/overview/exercise-details/open-code-editor-button/open-code-editor-button.component';

@Component({
    imports: [
        ProgrammingExerciseParticipationActionsComponent,
        NgTemplateOutlet,
        ExerciseActionButtonComponent,
        RouterLink,
        NgbTooltip,
        OpenCodeEditorButtonComponent,
        CodeButtonComponent,
        RequestFeedbackButtonComponent,
        ArtemisTranslatePipe,
    ],
    providers: [ExternalCloningService],
    selector: 'jhi-exercise-details-student-actions',
    templateUrl: './exercise-details-student-actions.component.html',
    styleUrls: ['../../course-overview/course-overview.scss'],
    host: {
        '[class.col]': 'equalColumns()',
        '[class.col-auto]': 'smallColumns()',
    },
})
export class ExerciseDetailsStudentActionsComponent extends StudentExerciseActionsDirective {
    protected readonly faFolderOpen = faFolderOpen;
    protected readonly faUsers = faUsers;
    protected readonly faEye = faEye;
    protected readonly faPlayCircle = faPlayCircle;
    protected readonly faRedo = faRedo;

    protected readonly ExerciseType = ExerciseType;
    protected readonly InitializationState = InitializationState;

    readonly equalColumns = input(true);
    readonly smallColumns = input(false);

    readonly programmingParticipationForCodeButton = computed(() => this.getProgrammingParticipationForCodeButton());
    readonly gradedRepositoryUri = computed(() => this.getGradedRepositoryUri());

    private getProgrammingParticipationForCodeButton(): ProgrammingExerciseStudentParticipation | undefined {
        const participations = this.studentParticipations();
        return this.participationService.getSpecificStudentParticipation(participations, false) ?? participations[0];
    }

    private getGradedRepositoryUri(): string {
        const participation = this.programmingParticipationForCodeButton();
        return participation && this.isProgrammingExerciseStudentParticipation(participation) ? (participation.repositoryUri ?? '') : '';
    }

    private isProgrammingExerciseStudentParticipation(participation: StudentParticipation): participation is ProgrammingExerciseStudentParticipation {
        return participation.type === ParticipationType.PROGRAMMING;
    }
}
