import { Component, OnInit } from '@angular/core';
import { ProgrammingExerciseParticipationService } from 'app/entities/programming-exercise/services/programming-exercise-participation.service';
import { ProgrammingExerciseService } from 'app/entities/programming-exercise/services/programming-exercise.service';
import { ParticipationService } from 'app/entities/participation/participation.service';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseExerciseService } from 'app/entities/course/course.service';
import { CodeEditorFileService, CodeEditorSessionService, DomainService } from 'app/code-editor/service';
import { TranslateService } from '@ngx-translate/core';
import { JhiAlertService } from 'ng-jhipster';
import { CodeEditorInstructorBaseContainerComponent, REPOSITORY } from 'app/code-editor/instructor/code-editor-instructor-base-container.component';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';
import { IdeBuildAndTestService } from 'app/intellij/ide-build-and-test.service';
import { IntelliJState } from 'app/intellij/intellij';

@Component({
    selector: 'jhi-code-editor-instructor-intellij',
    templateUrl: './code-editor-instructor-intellij-container.component.html',
    styles: ['.instructions-intellij { height: 700px }'],
})
export class CodeEditorInstructorIntellijContainerComponent extends CodeEditorInstructorBaseContainerComponent implements OnInit {
    intellijState: IntelliJState;

    constructor(
        private javaBridge: JavaBridgeService,
        private intellijBuildAndTestService: IdeBuildAndTestService,
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

    ngOnInit(): void {
        super.ngOnInit();
        this.javaBridge.state().subscribe(state => (this.intellijState = state));
    }

    protected applyDomainChange(domainType: any, domainValue: any) {
        super.applyDomainChange(domainType, domainValue);
        this.javaBridge.selectInstructorRepository(this.selectedRepository);
    }

    selectSolutionParticipation() {
        this.router.navigateByUrl(`/code-editor/ide/${this.exercise.id}/admin/${this.exercise.solutionParticipation.id}`);
    }

    selectTemplateParticipation() {
        this.router.navigateByUrl(`/code-editor/ide/${this.exercise.id}/admin/${this.exercise.templateParticipation.id}`);
    }

    selectAssignmentParticipation() {
        this.router.navigateByUrl(`/code-editor/ide/${this.exercise.id}/admin/${this.exercise.studentParticipations[0].id}`);
    }

    selectTestRepository() {
        this.router.navigateByUrl(`/code-editor/ide/${this.exercise.id}/admin/test`);
    }

    /**
     * Submits the code of the selected repository and tells IntelliJ to listen to any new test results for the selected repo.
     * Submitting means committing all changes and pushing them to the remote.
     */
    submit(): void {
        this.javaBridge.submitInstructorRepository();
        if (this.selectedRepository !== REPOSITORY.TEST) {
            this.intellijState.building = true;
            this.intellijBuildAndTestService.listenOnBuildOutputAndForwardChanges(this.exercise, this.selectedParticipation);
        }
    }

    /**
     * Tells IntelliJ to build and test the selected repository locally instead of committing and pushing the code to the remote
     */
    buildLocally(): void {
        this.javaBridge.buildAndTestInstructorRepository();
        this.intellijState.building = true;
    }
}
