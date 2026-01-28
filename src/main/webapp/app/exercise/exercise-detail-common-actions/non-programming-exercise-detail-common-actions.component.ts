import { Component, Input, OnInit, inject } from '@angular/core';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Subject } from 'rxjs';
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

@Component({
    selector: 'jhi-non-programming-exercise-detail-common-actions',
    templateUrl: './non-programming-exercise-detail-common-actions.component.html',
    imports: [RouterLink, FaIconComponent, TranslateDirective, NgbTooltip, DeleteButtonDirective, ArtemisTranslatePipe, FeatureOverlayComponent],
})
export class NonProgrammingExerciseDetailCommonActionsComponent implements OnInit {
    private textExerciseService = inject(TextExerciseService);
    private fileUploadExerciseService = inject(FileUploadExerciseService);
    private modelingExerciseService = inject(ModelingExerciseService);
    private profileService = inject(ProfileService);
    private eventManager = inject(EventManager);
    private router = inject(Router);

    @Input()
    exercise: Exercise;

    @Input()
    course: Course;

    @Input()
    isExamExercise = false;

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
        if (!this.isExamExercise) {
            this.baseResource = `/course-management/${this.course.id!}/${this.exercise.type}-exercises/${this.exercise.id}/`;
            this.teamBaseResource = `/course-management/${this.course.id!}/exercises/${this.exercise.id}/`;
            this.shortBaseResource = `/course-management/${this.course.id!}/`;
        } else {
            this.baseResource =
                `/course-management/${this.course.id!}/exams/${this.exercise.exerciseGroup?.exam?.id}` +
                `/exercise-groups/${this.exercise.exerciseGroup?.id}/${this.exercise.type}-exercises/${this.exercise.id}/`;
            this.teamBaseResource =
                `/course-management/${this.course.id!}/exams/${this.exercise.exerciseGroup?.exam?.id}` +
                `/exercise-groups/${this.exercise.exerciseGroup?.id}/exercises/${this.exercise.id}/`;
            this.shortBaseResource = `/course-management/${this.course.id!}/exams/${this.exercise.exerciseGroup?.exam?.id}/`;
        }
        this.irisEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);
        this.plagiarismEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_PLAGIARISM);
    }

    deleteExercise() {
        switch (this.exercise.type) {
            case ExerciseType.TEXT:
                this.textExerciseService.delete(this.exercise.id!).subscribe({
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
                this.fileUploadExerciseService.delete(this.exercise.id!).subscribe({
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
                this.modelingExerciseService.delete(this.exercise.id!).subscribe({
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
        if (!this.isExamExercise) {
            this.router.navigateByUrl(`/course-management/${this.course.id}/exercises`);
        } else {
            this.router.navigateByUrl(`/course-management/${this.course.id}/exams/${this.exercise.exerciseGroup?.exam?.id}/exercise-groups`);
        }
    }
}
