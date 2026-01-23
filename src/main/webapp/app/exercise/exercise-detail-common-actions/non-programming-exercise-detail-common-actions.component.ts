import { Component, OnInit, computed, inject, input } from '@angular/core';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Observable, Subject } from 'rxjs';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { HttpErrorResponse } from '@angular/common/http';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Router, RouterLink } from '@angular/router';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { EventManager } from 'app/shared/service/event-manager.service';
import { faBook, faChartBar, faListAlt, faRobot, faTable, faTrash, faUserCheck, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_IRIS, MODULE_FEATURE_PLAGIARISM } from 'app/app.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FeatureOverlayComponent } from 'app/shared/components/feature-overlay/feature-overlay.component';
import { EntitySummary } from 'app/shared/delete-dialog/delete-dialog.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';

@Component({
    selector: 'jhi-non-programming-exercise-detail-common-actions',
    templateUrl: './non-programming-exercise-detail-common-actions.component.html',
    imports: [RouterLink, FaIconComponent, TranslateDirective, NgbTooltip, DeleteButtonDirective, ArtemisTranslatePipe, FeatureOverlayComponent],
})
export class NonProgrammingExerciseDetailCommonActionsComponent implements OnInit {
    private textExerciseService = inject(TextExerciseService);
    private fileUploadExerciseService = inject(FileUploadExerciseService);
    private modelingExerciseService = inject(ModelingExerciseService);
    private exerciseService = inject(ExerciseService);
    private profileService = inject(ProfileService);
    private eventManager = inject(EventManager);
    private router = inject(Router);

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
     * This aligns with the access rights documented in docs/admin/access-rights.mdx
     */
    canAccessParticipationsAndScores = computed(() => {
        const exercise = this.exercise();
        return (exercise?.isAtLeastTutor && !this.isExamExercise()) || !!exercise?.isAtLeastInstructor;
    });

    dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    teamBaseResource: string;
    baseResource: string;
    shortBaseResource: string;
    irisEnabled = false;
    plagiarismEnabled = false;
    readonly ExerciseType = ExerciseType;

    readonly AssessmentType = AssessmentType;

    // Icons
    faTrash = faTrash;
    faBook = faBook;
    faWrench = faWrench;
    faUsers = faUsers;
    faTable = faTable;
    faListAlt = faListAlt;
    faChartBar = faChartBar;
    faUserCheck = faUserCheck;
    faRobot = faRobot;

    ngOnInit(): void {
        const exercise = this.exercise();
        const course = this.course();
        if (!this.isExamExercise()) {
            this.baseResource = `/course-management/${course.id!}/${exercise.type}-exercises/${exercise.id}/`;
            this.teamBaseResource = `/course-management/${course.id!}/exercises/${exercise.id}/`;
            this.shortBaseResource = `/course-management/${course.id!}/`;
        } else {
            this.baseResource =
                `/course-management/${course.id!}/exams/${exercise.exerciseGroup?.exam?.id}` +
                `/exercise-groups/${exercise.exerciseGroup?.id}/${exercise.type}-exercises/${exercise.id}/`;
            this.teamBaseResource =
                `/course-management/${course.id!}/exams/${exercise.exerciseGroup?.exam?.id}` + `/exercise-groups/${exercise.exerciseGroup?.id}/exercises/${exercise.id}/`;
            this.shortBaseResource = `/course-management/${course.id!}/exams/${exercise.exerciseGroup?.exam?.id}/`;
        }
        this.irisEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);
        this.plagiarismEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_PLAGIARISM);
    }

    deleteExercise() {
        const exercise = this.exercise();
        switch (exercise.type) {
            case ExerciseType.TEXT:
                this.textExerciseService.delete(exercise.id!).subscribe({
                    next: () => {
                        this.eventManager.broadcast({
                            name: 'textExerciseListModification',
                            content: 'Deleted a textExercise',
                        });
                        this.dialogErrorSource.next('');
                        this.navigateToOverview();
                    },
                    error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
                });
                break;
            case ExerciseType.FILE_UPLOAD:
                this.fileUploadExerciseService.delete(exercise.id!).subscribe({
                    next: () => {
                        this.eventManager.broadcast({
                            name: 'fileUploadExerciseListModification',
                            content: 'Deleted an fileUploadExercise',
                        });
                        this.dialogErrorSource.next('');
                        this.navigateToOverview();
                    },
                    error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
                });
                break;
            case ExerciseType.MODELING:
                this.modelingExerciseService.delete(exercise.id!).subscribe({
                    next: () => {
                        this.eventManager.broadcast({
                            name: 'modelingExerciseListModification',
                            content: 'Deleted an modelingExercise',
                        });
                        this.dialogErrorSource.next('');
                        this.navigateToOverview();
                    },
                    error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
                });
                break;
            default:
        }
    }

    /**
     * Navigates back to the exercises list
     */
    private navigateToOverview() {
        const course = this.course();
        const exercise = this.exercise();
        if (!this.isExamExercise()) {
            this.router.navigateByUrl(`/course-management/${course.id}/exercises`);
        } else {
            this.router.navigateByUrl(`/course-management/${course.id}/exams/${exercise.exerciseGroup?.exam?.id}/exercise-groups`);
        }
    }

    fetchExerciseDeletionSummary(exerciseId: number): Observable<EntitySummary> {
        return this.exerciseService.getDeletionSummary(exerciseId);
    }
}
