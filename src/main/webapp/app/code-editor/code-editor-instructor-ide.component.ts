import { Component, OnInit } from '@angular/core';
import { ProgrammingExerciseParticipationService, ProgrammingExerciseService } from 'app/entities/programming-exercise';
import { ParticipationService } from 'app/entities/participation';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseExerciseService } from 'app/entities/course';
import { CodeEditorFileService, CodeEditorSessionService, DomainService } from 'app/code-editor/service';
import { TranslateService } from '@ngx-translate/core';
import { JhiAlertService } from 'ng-jhipster';
import { CodeEditorInstructorBaseContainerComponent, REPOSITORY } from 'app/code-editor/code-editor-instructor-base-container.component';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';
import { IdeBuildAndTestService } from 'app/intellij/ide-build-and-test.service';
import { IntelliJState } from 'app/intellij/intellij';

@Component({
    selector: 'jhi-code-editor-instructor-ide',
    templateUrl: './code-editor-instructor-ide.component.html',
    styles: ['.instructions-intellij { height: 500px }'],
})
export class CodeEditorInstructorIdeComponent extends CodeEditorInstructorBaseContainerComponent implements OnInit {
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

    submit(): void {
        this.javaBridge.submitInstructorRepository();
        if (this.selectedRepository !== REPOSITORY.TEST) {
            this.intellijState.building = true;
            this.intellijBuildAndTestService.listenOnBuildOutputAndForwardChanges(this.exercise, this.selectedParticipation);
        }
    }

    buildLocally(): void {
        this.javaBridge.buildAndTestInstructorRepository();
        this.intellijState.building = true;
    }
}
