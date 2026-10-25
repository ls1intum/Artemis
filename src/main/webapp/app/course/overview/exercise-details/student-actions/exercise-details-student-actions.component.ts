import { ProgrammingExerciseParticipationActionsComponent } from 'app/exercise/participation/programming-exercise-participation-actions/programming-exercise-participation-actions.component';
import { StudentExerciseActionsDirective } from 'app/exercise/participation/student-exercise-actions.directive';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ExternalCloningService } from 'app/programming/shared/services/external-cloning.service';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

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
}
