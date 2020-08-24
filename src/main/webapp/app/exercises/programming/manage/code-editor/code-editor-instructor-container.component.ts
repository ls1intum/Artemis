import { Component, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/alert/alert.service';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { CodeEditorInstructorBaseContainerComponent } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-base-container.component';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';

@Component({
    selector: 'jhi-code-editor-instructor',
    templateUrl: './code-editor-instructor-container.component.html',
})
export class CodeEditorInstructorContainerComponent extends CodeEditorInstructorBaseContainerComponent {
    @ViewChild(UpdatingResultComponent, { static: false }) resultComp: UpdatingResultComponent;
    @ViewChild(ProgrammingExerciseEditableInstructionComponent, { static: false }) editableInstructions: ProgrammingExerciseEditableInstructionComponent;

    constructor(
        router: Router,
        exerciseService: ProgrammingExerciseService,
        courseExerciseService: CourseExerciseService,
        domainService: DomainService,
        programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        exerciseHintService: ExerciseHintService,
        location: Location,
        participationService: ParticipationService,
        translateService: TranslateService,
        route: ActivatedRoute,
        jhiAlertService: AlertService,
    ) {
        super(
            router,
            exerciseService,
            courseExerciseService,
            domainService,
            programmingExerciseParticipationService,
            exerciseHintService,
            location,
            participationService,
            route,
            jhiAlertService,
        );
    }

    /**
     * Select the solution participation repository and navigate to it
     */
    selectSolutionParticipation() {
        this.router.navigate(['..', this.exercise.solutionParticipation.id], { relativeTo: this.route });
    }

    /**
     * Select the template participation repository and navigate to it
     */
    selectTemplateParticipation() {
        this.router.navigate(['..', this.exercise.templateParticipation.id], { relativeTo: this.route });
    }

    /**
     * Select the assignment participation repository and navigate to it
     */
    selectAssignmentParticipation() {
        this.router.navigate(['..', this.exercise.studentParticipations[0].id], { relativeTo: this.route });
    }

    /**
     * Select the test repository and navigate to it
     */
    selectTestRepository() {
        this.router.navigate(['..', 'test'], { relativeTo: this.route });
    }

    onResizeEditorInstructions() {
        if (this.editableInstructions.markdownEditor && this.editableInstructions.markdownEditor.aceEditorContainer) {
            this.editableInstructions.markdownEditor.aceEditorContainer.getEditor().resize();
        }
    }
}
