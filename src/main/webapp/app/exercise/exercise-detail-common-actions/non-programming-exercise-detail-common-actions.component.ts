import { TumAetUiButtonDirective, TumAetUiMenuComponent, TumAetUiMenuItemDirective, TumAetUiMenuTriggerDirective, TumAetUiTooltipDirective } from '@tumaet/ui-angular';

import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Observable, Subject } from 'rxjs';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { HttpErrorResponse } from '@angular/common/http';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Router, RouterLink } from '@angular/router';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { faBook, faChartBar, faEllipsis, faListAlt, faRobot, faTrash, faUserCheck, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_IRIS, MODULE_FEATURE_PLAGIARISM } from 'app/app.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExerciseDetailActionsComponent } from 'app/exercise/detail/exercise-detail-actions.component';
import { EntitySummaryCategory } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { EntityDeletionProgress } from 'app/shared-ui/delete-dialog/entity-deletion-progress.model';
import { EntityDeletionService } from 'app/shared-ui/delete-dialog/service/entity-deletion.service';
import { exerciseDeletionTarget, isDeletionCompleted, trackAlive } from 'app/shared-ui/delete-dialog/entity-deletion-target.util';
import { ExerciseService } from 'app/exercise/services/exercise.service';

@Component({
    selector: 'jhi-non-programming-exercise-detail-common-actions',
    templateUrl: './non-programming-exercise-detail-common-actions.component.html',
    imports: [
        TumAetUiTooltipDirective,
        TumAetUiButtonDirective,
        RouterLink,
        FaIconComponent,
        TranslateDirective,
        DeleteButtonDirective,
        ArtemisTranslatePipe,
        ExerciseDetailActionsComponent,
        TumAetUiMenuComponent,
        TumAetUiMenuItemDirective,
        TumAetUiMenuTriggerDirective,
    ],
})
export class NonProgrammingExerciseDetailCommonActionsComponent implements OnInit {
    private textExerciseService = inject(TextExerciseService);
    private fileUploadExerciseService = inject(FileUploadExerciseService);
    private modelingExerciseService = inject(ModelingExerciseService);
    private exerciseService = inject(ExerciseService);
    private entityDeletionService = inject(EntityDeletionService);
    private profileService = inject(ProfileService);
    private eventManager = inject(EventManager);
    private router = inject(Router);
    private destroyRef = inject(DestroyRef);
    private readonly isAlive = trackAlive(this.destroyRef);

    readonly section = input<'actions' | 'configuration' | 'administration'>('actions');
    exercise = input.required<Exercise>();
    course = input.required<Course>();
    isExamExercise = input<boolean>(false);

    /**
     * Determines if the current user can access participations and scores for this exercise.
     *
     * Access rules based on exercise context:
     * - Course exercises: Teaching assistants (tutors) and above can access
     * - Exam exercises: Only instructors and above can access (more restrictive for exam confidentiality)
     *
     * This aligns with the access rights documented in documentation/docs/admin/access-rights.mdx
     */
    canAccessParticipationsAndScores = computed(() => {
        const exercise = this.exercise();
        return (exercise?.isAtLeastTutor && !this.isExamExercise()) || !!exercise?.isAtLeastInstructor;
    });

    dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    readonly shortBaseResource = computed(() => {
        const coursePath = `/course-management/${this.course().id!}/`;
        return this.isExamExercise() ? `${coursePath}exams/${this.exercise().exerciseGroup?.exam?.id}/` : coursePath;
    });
    private readonly exerciseBaseResource = computed(() =>
        this.isExamExercise() ? `${this.shortBaseResource()}exercise-groups/${this.exercise().exerciseGroup?.id}/` : this.shortBaseResource(),
    );
    readonly teamBaseResource = computed(() => `${this.exerciseBaseResource()}exercises/${this.exercise().id}/`);
    readonly baseResource = computed(() => `${this.exerciseBaseResource()}${this.exercise().type}-exercises/${this.exercise().id}/`);
    readonly irisEnabled = signal(false);
    readonly plagiarismEnabled = signal(false);
    readonly ExerciseType = ExerciseType;

    readonly AssessmentType = AssessmentType;

    // Icons
    faEllipsis = faEllipsis;
    faTrash = faTrash;
    faBook = faBook;
    faWrench = faWrench;
    faUsers = faUsers;
    faListAlt = faListAlt;
    faChartBar = faChartBar;
    faUserCheck = faUserCheck;
    faRobot = faRobot;

    ngOnInit(): void {
        this.irisEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS));
        this.plagiarismEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_PLAGIARISM));
        if (this.exercise().isAtLeastInstructor) {
            this.resumeRunningDeletion();
        }
    }

    deleteExercise() {
        const exercise = this.exercise();
        switch (exercise.type) {
            case ExerciseType.TEXT:
                this.runDeletion(this.textExerciseService.delete(exercise.id!), 'textExerciseListModification', 'Deleted a textExercise');
                break;
            case ExerciseType.FILE_UPLOAD:
                this.runDeletion(this.fileUploadExerciseService.delete(exercise.id!), 'fileUploadExerciseListModification', 'Deleted an fileUploadExercise');
                break;
            case ExerciseType.MODELING:
                this.runDeletion(this.modelingExerciseService.delete(exercise.id!), 'modelingExerciseListModification', 'Deleted an modelingExercise');
                break;
            default:
        }
    }

    /**
     * Starts the background deletion, shows its progress and leaves the page of the exercise once it is gone.
     */
    private runDeletion(request$: Observable<unknown>, eventName: string, eventContent: string) {
        const exerciseId = this.exercise().id;
        this.entityDeletionService.delete(exerciseDeletionTarget(this.exercise()), request$).subscribe({
            next: (progress) => {
                this.onDeletionEnded(progress, exerciseId, eventName, eventContent);
                // A failed deletion is reported by the progress dialog; this only ends the confirmation dialog.
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Shows the progress of a deletion of this exercise that is already running, e.g. after the page was reloaded or another instructor started it.
     */
    private resumeRunningDeletion() {
        const exercise = this.exercise();
        this.entityDeletionService
            .resume(exerciseDeletionTarget(exercise), this.destroyRef)
            .subscribe((progress) =>
                this.onDeletionEnded(
                    progress,
                    exercise.id,
                    `${exercise.type === ExerciseType.FILE_UPLOAD ? 'fileUpload' : exercise.type}ExerciseListModification`,
                    'Deleted an exercise',
                ),
            );
    }

    private onDeletionEnded(progress: EntityDeletionProgress, deletedExerciseId: number | undefined, eventName: string, eventContent: string) {
        if (isDeletionCompleted(progress)) {
            this.eventManager.broadcast({ name: eventName, content: eventContent });
            // This component is reused for other exercises, so the page must still show the deleted exercise before it navigates away from it.
            if (this.isAlive() && this.exercise().id === deletedExerciseId) {
                this.navigateToOverview();
            }
        }
    }

    /**
     * Navigates back to the exercises list
     */
    private navigateToOverview() {
        const course = this.course();
        const exercise = this.exercise();
        if (!this.isExamExercise()) {
            void this.router.navigateByUrl(`/course-management/${course.id}/exercises`);
        } else {
            void this.router.navigateByUrl(`/course-management/${course.id}/exams/${exercise.exerciseGroup?.exam?.id}/exercise-groups`);
        }
    }

    fetchExerciseDeletionSummary(): Observable<EntitySummaryCategory[]> {
        return this.exerciseService.getDeletionSummary(this.exercise());
    }
}
