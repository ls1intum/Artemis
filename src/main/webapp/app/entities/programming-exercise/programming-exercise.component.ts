import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { ProgrammingExercise } from './programming-exercise.model';
import { ProgrammingExerciseService } from './services/programming-exercise.service';
import { CourseService } from 'app/entities/course/course.service';
import { CourseExerciseService } from 'app/entities/course/course.service';
import { ActivatedRoute, Router } from '@angular/router';
import { ExerciseComponent } from 'app/entities/exercise/exercise.component';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseService } from 'app/entities/exercise';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { onError } from 'app/utils/global.utils';
import { AccountService } from 'app/core/auth/account.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseImportComponent } from 'app/entities/programming-exercise/programming-exercise-import.component';
import { FeatureToggle } from 'app/feature-toggle';
import { IntelliJState, isIntelliJ } from 'app/intellij/intellij';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';
import { stringifyCircular } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-programming-exercise',
    templateUrl: './programming-exercise.component.html',
})
export class ProgrammingExerciseComponent extends ExerciseComponent implements OnInit, OnDestroy {
    @Input() programmingExercises: ProgrammingExercise[];
    readonly ActionType = ActionType;
    readonly isIntelliJ = isIntelliJ;
    FeatureToggle = FeatureToggle;
    intelliJState: IntelliJState;

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private courseExerciseService: CourseExerciseService,
        private exerciseService: ExerciseService,
        private accountService: AccountService,
        private jhiAlertService: JhiAlertService,
        private modalService: NgbModal,
        private router: Router,
        private javaBridge: JavaBridgeService,
        courseService: CourseService,
        translateService: TranslateService,
        eventManager: JhiEventManager,
        route: ActivatedRoute,
    ) {
        super(courseService, translateService, route, eventManager);
        this.programmingExercises = [];
    }

    ngOnInit(): void {
        super.ngOnInit();
        this.javaBridge.state().subscribe(state => (this.intelliJState = state));
    }

    protected loadExercises(): void {
        this.courseExerciseService.findAllProgrammingExercisesForCourse(this.courseId).subscribe(
            (res: HttpResponse<ProgrammingExercise[]>) => {
                this.programmingExercises = res.body!;
                // reconnect exercise with course
                this.programmingExercises.forEach(exercise => {
                    exercise.course = this.course;
                    exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(exercise.course);
                    exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(exercise.course);
                });
                this.emitExerciseCount(this.programmingExercises.length);
            },
            (res: HttpErrorResponse) => onError(this.jhiAlertService, res),
        );
    }

    trackId(index: number, item: ProgrammingExercise) {
        return item.id;
    }

    /**
     * Deletes programming exercise
     * @param programmingExerciseId the id of the programming exercise that we want to delete
     * @param $event contains additional checks for deleting exercise
     */
    deleteProgrammingExercise(programmingExerciseId: number, $event: { [key: string]: boolean }) {
        return this.programmingExerciseService.delete(programmingExerciseId, $event.deleteStudentReposBuildPlans, $event.deleteBaseReposBuildPlans).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'programmingExerciseListModification',
                    content: 'Deleted an programmingExercise',
                });
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    /**
     * Resets programming exercise
     * @param programmingExerciseId the id of the programming exercise that we want to delete
     */
    resetProgrammingExercise(programmingExerciseId: number) {
        this.exerciseService.reset(programmingExerciseId).subscribe(
            () => this.dialogErrorSource.next(''),
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    protected getChangeEventName(): string {
        return 'programmingExerciseListModification';
    }

    callback() {}

    openImportModal() {
        const modalRef = this.modalService.open(ProgrammingExerciseImportComponent, { size: 'lg', backdrop: 'static' });
        modalRef.result.then(
            (result: ProgrammingExercise) => {
                this.router.navigate(['course', this.courseId, 'programming-exercise', 'import', result.id]);
            },
            reason => {},
        );
    }

    editIntIntelliJ(programmingExercise: ProgrammingExercise) {
        this.javaBridge.editExercise(stringifyCircular(programmingExercise));
    }

    openIntelliJEditor(exercise: ProgrammingExercise) {
        try {
            this.router.navigate(['code-editor', 'ide', exercise.id, 'admin', exercise.templateParticipation.id]);
        } catch (e) {
            this.javaBridge.log(e);
        }
    }
}
