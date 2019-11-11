import { Component } from '@angular/core';
import { ProgrammingExerciseParticipationService, ProgrammingExerciseService } from 'app/entities/programming-exercise';
import { ParticipationService } from 'app/entities/participation';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseExerciseService } from 'app/entities/course';
import { CodeEditorFileService, CodeEditorSessionService, DomainService } from 'app/code-editor/service';
import { TranslateService } from '@ngx-translate/core';
import { JhiAlertService } from 'ng-jhipster';
import { CodeEditorInstructorBaseContainerComponent } from 'app/code-editor/code-editor-instructor-base-container.component';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';

@Component({
    selector: 'jhi-code-editor-instructor-ide',
    templateUrl: './code-editor-instructor-ide.component.html',
})
export class CodeEditorInstructorIdeComponent extends CodeEditorInstructorBaseContainerComponent {
    constructor(
        private javaBridge: JavaBridgeService,
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
    }

    buildLocally(): void {
        this.javaBridge.buildAndTestInstructorRepository();
    }
}
