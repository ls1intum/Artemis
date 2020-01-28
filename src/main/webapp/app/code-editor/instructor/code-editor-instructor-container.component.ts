import { Component, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ProgrammingExerciseParticipationService } from 'app/entities/programming-exercise/services/programming-exercise-participation.service';
import { ProgrammingExerciseService } from 'app/entities/programming-exercise/services/programming-exercise.service';
import { CourseExerciseService } from 'app/entities/course/course.service';
import { ParticipationService } from 'app/entities/participation/participation.service';
import { TranslateService } from '@ngx-translate/core';
import { CodeEditorFileService, DomainService } from 'app/code-editor/service';
import { JhiAlertService } from 'ng-jhipster';
import {
    CodeEditorAceComponent,
    CodeEditorActionsComponent,
    CodeEditorBuildOutputComponent,
    CodeEditorFileBrowserComponent,
    CodeEditorInstructionsComponent,
    CodeEditorSessionService,
} from 'app/code-editor';
import { UpdatingResultComponent } from 'app/entities/result';
import { CodeEditorInstructorBaseContainerComponent } from 'app/code-editor/instructor/code-editor-instructor-base-container.component';

@Component({
    selector: 'jhi-code-editor-instructor',
    templateUrl: './code-editor-instructor-container.component.html',
})
export class CodeEditorInstructorContainerComponent extends CodeEditorInstructorBaseContainerComponent {
    @ViewChild(CodeEditorFileBrowserComponent, { static: false }) fileBrowser: CodeEditorFileBrowserComponent;
    @ViewChild(CodeEditorActionsComponent, { static: false }) actions: CodeEditorActionsComponent;
    @ViewChild(CodeEditorBuildOutputComponent, { static: false }) buildOutput: CodeEditorBuildOutputComponent;
    @ViewChild(CodeEditorInstructionsComponent, { static: false }) instructions: CodeEditorInstructionsComponent;
    @ViewChild(CodeEditorAceComponent, { static: false }) aceEditor: CodeEditorAceComponent;
    @ViewChild(UpdatingResultComponent, { static: false }) resultComp: UpdatingResultComponent;

    constructor(
        router: Router,
        exerciseService: ProgrammingExerciseService,
        courseExerciseService: CourseExerciseService,
        domainService: DomainService,
        programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        participationService: ParticipationService,
        translateService: TranslateService,
        route: ActivatedRoute,
        jhiAlertService: JhiAlertService,
        sessionService: CodeEditorSessionService,
        fileService: CodeEditorFileService,
    ) {
        super(
            router,
            exerciseService,
            courseExerciseService,
            domainService,
            programmingExerciseParticipationService,
            participationService,
            translateService,
            route,
            jhiAlertService,
            sessionService,
            fileService,
        );
    }

    selectSolutionParticipation() {
        this.router.navigateByUrl(`/code-editor/${this.exercise.id}/admin/${this.exercise.solutionParticipation.id}`);
    }

    selectTemplateParticipation() {
        this.router.navigateByUrl(`/code-editor/${this.exercise.id}/admin/${this.exercise.templateParticipation.id}`);
    }

    selectAssignmentParticipation() {
        this.router.navigateByUrl(`/code-editor/${this.exercise.id}/admin/${this.exercise.studentParticipations[0].id}`);
    }

    selectTestRepository() {
        this.router.navigateByUrl(`/code-editor/${this.exercise.id}/admin/test`);
    }
}
