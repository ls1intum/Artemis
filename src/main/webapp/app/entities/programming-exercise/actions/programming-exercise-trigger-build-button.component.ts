import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { ShowdownExtension } from 'showdown';
import { catchError, filter, flatMap, map, switchMap, tap } from 'rxjs/operators';
import { Feedback } from 'app/entities/feedback';
import { Result, ResultService } from 'app/entities/result';
import { ProgrammingExercise } from '../programming-exercise.model';
import { RepositoryFileService } from 'app/entities/repository';
import { hasParticipationChanged, Participation, ParticipationWebsocketService } from 'app/entities/participation';
import { merge, Observable, Subscription } from 'rxjs';
import { problemStatementHasChanged } from 'app/entities/exercise';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { ProgrammingExerciseTaskExtensionWrapper } from './extensions/programming-exercise-task.extension';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/entities/programming-exercise/instructions/extensions/programming-exercise-plant-uml.extension';
import { ProgrammingExerciseInstructionService } from 'app/entities/programming-exercise/instructions/programming-exercise-instruction.service';
import { TaskArray } from 'app/entities/programming-exercise/instructions/programming-exercise-task.model';
import { ProgrammingExerciseParticipationService } from 'app/entities/programming-exercise/services';
import { ProgrammingSubmissionWebsocketService } from 'app/submission/programming-submission-websocket.service';

@Component({
    selector: 'jhi-programming-exercise-trigger-build-button',
    templateUrl: './programming-exercise-trigger-build-button.component.html',
})
export class ProgrammingExerciseTriggerBuildButtonComponent {
    @Input participationId: number;

    constructor(private participationService: ProgrammingExerciseParticipationService, private submissionService: ProgrammingSubmissionWebsocketService) {}

    triggerBuild() {
        this.participationService.triggerBuild(this.participationId).subscribe();
    }
}
