import { Component, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExerciseScoresExportButtonComponent } from 'app/exercise/exercise-scores/exercise-scores-export-button.component';
import { merge } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ExerciseComponent } from 'app/exercise/exercise.component';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { onError } from 'app/shared/util/global.utils';
import { AccountService } from 'app/core/auth/account.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExerciseService } from 'app/exercise/exercise.service';
import { SortService } from 'app/shared/service/sort.service';
import { ProgrammingExerciseEditSelectedComponent } from 'app/programming/manage/programming-exercise-edit-selected.component';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming/programming-exercise-participation.model';
import { AlertService } from 'app/shared/service/alert.service';
import { createBuildPlanUrl } from 'app/programming/shared/utils/programming-exercise.utils';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ConsistencyCheckComponent } from 'app/shared/consistency-check/consistency-check.component';
import {
    faBook,
    faCheckDouble,
    faDownload,
    faFileSignature,
    faListAlt,
    faPencilAlt,
    faPlus,
    faSort,
    faTable,
    faTrash,
    faUndo,
    faUsers,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { PROFILE_LOCALCI, PROFILE_LOCALVC, PROFILE_THEIA } from 'app/app.constants';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { FormsModule } from '@angular/forms';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink } from '@angular/router';
import { ProgrammingExerciseGradingDirtyWarningComponent } from './grading/programming-exercise-grading-dirty-warning.component';
import { ProgrammingExerciseInstructorStatusComponent } from 'app/programming/manage/status/programming-exercise-instructor-status.component';
import { ExerciseCategoriesComponent } from 'app/shared/exercise-categories/exercise-categories.component';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { ProgrammingExerciseResetButtonDirective } from 'app/programming/manage/reset/programming-exercise-reset-button.directive';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ProgrammingAssessmentRepoExportButtonComponent } from 'app/programming/manage/assess/repo-export/programming-assessment-repo-export-button.component';
import { SlicePipe } from '@angular/common';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { RepositoryType } from '../shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-programming-exercise',
    templateUrl: './programming-exercise.component.html',
    imports: [
        SortDirective,
        FormsModule,
        SortByDirective,
        TranslateDirective,
        FaIconComponent,
        RouterLink,
        ProgrammingExerciseGradingDirtyWarningComponent,
        ProgrammingExerciseInstructorStatusComponent,
        ExerciseCategoriesComponent,
        FeatureToggleLinkDirective,
        ProgrammingExerciseResetButtonDirective,
        FeatureToggleDirective,
        DeleteButtonDirective,
        ProgrammingAssessmentRepoExportButtonComponent,
        ExerciseScoresExportButtonComponent,
        SlicePipe,
        ArtemisDatePipe,
        // TODO: the extension point for Orion does not work with Angular 19, we need to find a different solution
        // ExtensionPointDirective,
    ],
})
export class ProgrammingExerciseComponent extends ExerciseComponent implements OnInit, OnDestroy {
    protected exerciseService = inject(ExerciseService); // needed in html code
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private courseExerciseService = inject(CourseExerciseService);
    private accountService = inject(AccountService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);
    private sortService = inject(SortService);
    private profileService = inject(ProfileService);

    @Input() programmingExercises: ProgrammingExercise[] = [];
    filteredProgrammingExercises: ProgrammingExercise[];
    readonly ActionType = ActionType;
    FeatureToggle = FeatureToggle;
    solutionParticipationType = ProgrammingExerciseParticipationType.SOLUTION;
    templateParticipationType = ProgrammingExerciseParticipationType.TEMPLATE;
    localVCEnabled = true;
    localCIEnabled = true;
    onlineIdeEnabled = false;

    // extension points, see shared/extension-point
    // TODO: the extension point for Orion does not work with Angular 19, we need to find a different solution
    // @ContentChild('overrideRepositoryAndBuildPlan') overrideRepositoryAndBuildPlan: TemplateRef<any>;
    // @ContentChild('overrideButtons') overrideButtons: TemplateRef<any>;
    private buildPlanLinkTemplate: string;
    protected readonly RepositoryType = RepositoryType;

    // Icons
    faSort = faSort;
    faPlus = faPlus;
    faDownload = faDownload;
    faUndo = faUndo;
    faBook = faBook;
    faWrench = faWrench;
    faCheckDouble = faCheckDouble;
    faUsers = faUsers;
    faTable = faTable;
    faTrash = faTrash;
    faListAlt = faListAlt;
    faPencilAlt = faPencilAlt;
    faFileSignature = faFileSignature;

    protected get exercises() {
        return this.programmingExercises;
    }

    ngOnInit(): void {
        super.ngOnInit();
    }

    protected loadExercises(): void {
        this.courseExerciseService.findAllProgrammingExercisesForCourse(this.courseId).subscribe({
            next: (res: HttpResponse<ProgrammingExercise[]>) => {
                this.programmingExercises = res.body!;
                this.profileService.getProfileInfo().subscribe((profileInfo) => {
                    this.buildPlanLinkTemplate = profileInfo.buildPlanURLTemplate;
                    this.localVCEnabled = profileInfo.activeProfiles.includes(PROFILE_LOCALVC);
                    this.localCIEnabled = profileInfo.activeProfiles.includes(PROFILE_LOCALCI);
                    this.onlineIdeEnabled = profileInfo.activeProfiles.includes(PROFILE_THEIA);
                });
                // reconnect exercise with course
                this.programmingExercises.forEach((exercise) => {
                    exercise.course = this.course;
                    this.accountService.setAccessRightsForExercise(exercise);
                    if (exercise.projectKey) {
                        if (exercise.solutionParticipation?.buildPlanId) {
                            exercise.solutionParticipation.buildPlanUrl = createBuildPlanUrl(
                                this.buildPlanLinkTemplate,
                                exercise.projectKey,
                                exercise.solutionParticipation.buildPlanId,
                            );
                        }
                        if (exercise.templateParticipation?.buildPlanId) {
                            exercise.templateParticipation.buildPlanUrl = createBuildPlanUrl(
                                this.buildPlanLinkTemplate,
                                exercise.projectKey,
                                exercise.templateParticipation.buildPlanId,
                            );
                        }
                    }
                    this.selectedExercises = [];
                });
                this.applyFilter();
                this.emitExerciseCount(this.programmingExercises.length);
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    protected applyFilter(): void {
        this.filteredProgrammingExercises = this.programmingExercises.filter((exercise) => this.filter.matchesExercise(exercise));
        this.emitFilteredExerciseCount(this.filteredProgrammingExercises.length);
    }

    trackId(_index: number, item: ProgrammingExercise) {
        return item.id;
    }

    /**
     * Deletes programming exercise
     * @param programmingExerciseId the id of the programming exercise that we want to delete
     * @param event contains additional checks for deleting exercise
     */
    deleteProgrammingExercise(programmingExerciseId: number, event: { [key: string]: boolean }) {
        return this.programmingExerciseService.delete(programmingExerciseId, event.deleteStudentReposBuildPlans, event.deleteBaseReposBuildPlans).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'programmingExerciseListModification',
                    content: 'Deleted an programmingExercise',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Deletes all the given programming exercises
     * @param exercisesToDelete the exercise objects which are to be deleted
     * @param event contains additional checks which are performed for all these exercises
     */
    deleteMultipleProgrammingExercises(exercisesToDelete: ProgrammingExercise[], event: { [key: string]: boolean }) {
        const deletionObservables = exercisesToDelete.map((exercise) =>
            this.programmingExerciseService.delete(exercise.id!, event.deleteStudentReposBuildPlans, event.deleteBaseReposBuildPlans),
        );
        return merge(...deletionObservables).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'programmingExerciseListModification',
                    content: 'Deleted selected programmingExercises',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    protected getChangeEventName(): string {
        return 'programmingExerciseListModification';
    }

    sortRows() {
        this.sortService.sortByProperty(this.programmingExercises, this.predicate, this.reverse);
        this.applyFilter();
    }

    openEditSelectedModal() {
        const modalRef = this.modalService.open(ProgrammingExerciseEditSelectedComponent, {
            size: 'xl',
            backdrop: 'static',
        });
        modalRef.componentInstance.selectedProgrammingExercises = this.selectedExercises;
        modalRef.closed.subscribe(() => {
            location.reload();
        });
    }

    /**
     * Opens modal and executes a consistency check for the selected exercises
     */
    checkConsistencies() {
        const modalRef = this.modalService.open(ConsistencyCheckComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.exercisesToCheck = this.selectedExercises;
    }

    /**
     * Downloads the instructor repository. Used when the "localvc" profile is active.
     * For the local VCS, linking to an external site displaying the repository does not work.
     * Instead, the repository is downloaded.
     *
     * @param programmingExerciseId
     * @param repositoryType
     */
    downloadRepository(programmingExerciseId: number | undefined, repositoryType: RepositoryType) {
        if (programmingExerciseId) {
            // Repository type cannot be 'AUXILIARY' as auxiliary repositories are currently not supported for the local VCS.
            this.programmingExerciseService.exportInstructorRepository(programmingExerciseId, repositoryType, undefined).subscribe((response: HttpResponse<Blob>) => {
                downloadZipFileFromResponse(response);
                this.alertService.success('artemisApp.programmingExercise.export.successMessageRepos');
            });
        }
    }
}
