import { Component, OnInit } from '@angular/core';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/alert/alert.service';
import { CodeEditorInstructorBaseContainerComponent, REPOSITORY } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-base-container.component';
import { CodeEditorSessionService } from 'app/exercises/programming/shared/code-editor/service/code-editor-session.service';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { OrionBuildAndTestService } from 'app/shared/orion/orion-build-and-test.service';
import { OrionState } from 'app/shared/orion/orion';

@Component({
    selector: 'jhi-code-editor-instructor-orion',
    templateUrl: './code-editor-instructor-orion-container.component.html',
    styles: ['.instructions-orion { height: 700px }'],
})
export class CodeEditorInstructorOrionContainerComponent extends CodeEditorInstructorBaseContainerComponent implements OnInit {
    orionState: OrionState;

    constructor(
        private javaBridge: OrionConnectorService,
        private orionBuildAndTestService: OrionBuildAndTestService,
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

    ngOnInit(): void {
        super.ngOnInit();
        this.javaBridge.state().subscribe(state => (this.orionState = state));
    }

    protected applyDomainChange(domainType: any, domainValue: any) {
        super.applyDomainChange(domainType, domainValue);
        this.javaBridge.selectRepository(this.selectedRepository);
    }

    selectSolutionParticipation() {
        this.router.navigate(['..', this.exercise.solutionParticipation.id], { relativeTo: this.route });
    }

    selectTemplateParticipation() {
        this.router.navigate(['..', this.exercise.templateParticipation.id], { relativeTo: this.route });
    }

    selectAssignmentParticipation() {
        this.router.navigate(['..', this.exercise.studentParticipations[0].id], { relativeTo: this.route });
    }

    selectTestRepository() {
        this.router.navigate(['..', 'test'], { relativeTo: this.route });
    }

    /**
     * Submits the code of the selected repository and tells Orion to listen to any new test results for the selected repo.
     * Submitting means committing all changes and pushing them to the remote.
     */
    submit(): void {
        this.javaBridge.submitChanges();
        if (this.selectedRepository !== REPOSITORY.TEST) {
            this.orionState.building = true;
            this.orionBuildAndTestService.listenOnBuildOutputAndForwardChanges(this.exercise, this.selectedParticipation);
        }
    }

    /**
     * Tells Orion to build and test the selected repository locally instead of committing and pushing the code to the remote
     */
    buildLocally(): void {
        this.javaBridge.buildAndTestLocally();
        this.orionState.building = true;
    }
}
