import { Component, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/alert/alert.service';
import { UpdatingResultComponent } from 'app/entities/result/updating-result.component';
import { CodeEditorAceComponent } from 'app/code-editor/ace/code-editor-ace.component';
import { CodeEditorActionsComponent } from 'app/code-editor/actions/code-editor-actions.component';
import { CodeEditorBuildOutputComponent } from 'app/code-editor/build-output/code-editor-build-output.component';
import { CodeEditorFileBrowserComponent } from 'app/code-editor/file-browser/code-editor-file-browser.component';
import { CodeEditorInstructionsComponent } from 'app/code-editor/instructions/code-editor-instructions.component';
import { CodeEditorInstructorBaseContainerComponent } from 'app/code-editor/instructor/code-editor-instructor-base-container.component';
import { CodeEditorFileService } from 'app/code-editor/service/code-editor-file.service';
import { CodeEditorSessionService } from 'app/code-editor/service/code-editor-session.service';
import { CourseExerciseService } from 'app/entities/course/course.service';
import { DomainService } from 'app/code-editor/service/code-editor-domain.service';
import { ProgrammingExerciseParticipationService } from 'app/entities/programming-exercise/services/programming-exercise-participation.service';
import { ProgrammingExerciseService } from 'app/entities/programming-exercise/services/programming-exercise.service';
import { ParticipationService } from 'app/entities/participation/participation.service';

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
        jhiAlertService: AlertService,
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
