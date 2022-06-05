import { Component, OnInit } from '@angular/core';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/util/alert.service';
import { CodeEditorInstructorBaseContainerComponent, REPOSITORY } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-base-container.component';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { OrionBuildAndTestService } from 'app/shared/orion/orion-build-and-test.service';
import { OrionState } from 'app/shared/orion/orion';
import { faCircleNotch, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';

@Component({
    selector: 'jhi-code-editor-instructor-orion',
    templateUrl: './code-editor-instructor-and-editor-orion-container.component.html',
    styles: ['.instructions-orion { height: 700px }'],
})
export class CodeEditorInstructorAndEditorOrionContainerComponent extends CodeEditorInstructorBaseContainerComponent implements OnInit {
    orionState: OrionState;

    // Icons
    faCircleNotch = faCircleNotch;
    faTimesCircle = faTimesCircle;

    constructor(
        private orionConnectorService: OrionConnectorService,
        private orionBuildAndTestService: OrionBuildAndTestService,
        router: Router,
        exerciseService: ProgrammingExerciseService,
        courseExerciseService: CourseExerciseService,
        domainService: DomainService,
        programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        location: Location,
        participationService: ParticipationService,
        translateService: TranslateService,
        route: ActivatedRoute,
        alertService: AlertService,
    ) {
        super(router, exerciseService, courseExerciseService, domainService, programmingExerciseParticipationService, location, participationService, route, alertService);
    }

    /**
     * Calls ngOnInit of its superclass and initialize the subscription to
     * the Orion connector service, on component initialization
     */
    ngOnInit(): void {
        super.ngOnInit();
        this.orionConnectorService.state().subscribe((state) => (this.orionState = state));
    }

    protected applyDomainChange(domainType: any, domainValue: any) {
        super.applyDomainChange(domainType, domainValue);
        this.orionConnectorService.selectRepository(this.selectedRepository);
    }

    /**
     * Submits the code of the selected repository and tells Orion to listen to any new test results for the selected repo.
     * Submitting means committing all changes and pushing them to the remote.
     */
    submit(): void {
        this.orionConnectorService.submit();
        if (this.selectedRepository !== REPOSITORY.TEST) {
            this.orionConnectorService.isBuilding(true);
            this.orionBuildAndTestService.listenOnBuildOutputAndForwardChanges(this.exercise, this.selectedParticipation);
        }
    }

    /**
     * Tells Orion to build and test the selected repository locally instead of committing and pushing the code to the remote
     */
    buildLocally(): void {
        this.orionConnectorService.isBuilding(true);
        this.orionConnectorService.buildAndTestLocally();
    }
}
