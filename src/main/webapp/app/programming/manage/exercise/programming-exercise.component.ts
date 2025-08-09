import { Component, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExerciseScoresExportButtonComponent } from 'app/exercise/exercise-scores/export-button/exercise-scores-export-button.component';
import { merge } from 'rxjs';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ExerciseComponent } from 'app/exercise/exercise.component';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { onError } from 'app/shared/util/global.utils';
import { AccountService } from 'app/core/auth/account.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { SortService } from 'app/shared/service/sort.service';
import { ProgrammingExerciseEditSelectedComponent } from 'app/programming/manage/edit-selected/programming-exercise-edit-selected.component';
import { AlertService } from 'app/shared/service/alert.service';
import { createBuildPlanUrl } from 'app/programming/shared/utils/programming-exercise.utils';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { faBook, faCheckDouble, faDownload, faFileSignature, faListAlt, faPencilAlt, faPlus, faSort, faTable, faTrash, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { PROFILE_LOCALCI, PROFILE_THEIA } from 'app/app.constants';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { FormsModule } from '@angular/forms';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink } from '@angular/router';
import { ProgrammingExerciseGradingDirtyWarningComponent } from '../grading/warning/programming-exercise-grading-dirty-warning.component';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ProgrammingAssessmentRepoExportButtonComponent } from 'app/programming/manage/assess/repo-export/export-button/programming-assessment-repo-export-button.component';
import { SlicePipe } from '@angular/common';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { RepositoryType } from '../../shared/code-editor/model/code-editor.model';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';
import { ConsistencyCheckComponent } from 'app/programming/manage/consistency-check/consistency-check.component';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { SharingInfo } from 'app/sharing/sharing.model';
@Component({
    selector: 'jhi-programming-exercise',
    templateUrl: './programming-exercise.component.html',
    providers: [SharingInfo],
    imports: [
        SortDirective,
        FormsModule,
        SortByDirective,
        TranslateDirective,
        FaIconComponent,
        RouterLink,
        ProgrammingExerciseGradingDirtyWarningComponent,
        ExerciseCategoriesComponent,
        FeatureToggleLinkDirective,
        FeatureToggleDirective,
        DeleteButtonDirective,
        ProgrammingAssessmentRepoExportButtonComponent,
        ExerciseScoresExportButtonComponent,
        SlicePipe,
        ArtemisDatePipe,
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
    localCIEnabled = true;
    onlineIdeEnabled = false;
    numberOfResultsOfSolutionParticipation = 0;
    numberOfResultsOfTemplateParticipation = 0;

    private buildPlanLinkTemplate?: string; // only available on non LocalCI systems
    protected readonly RepositoryType = RepositoryType;

    // Icons
    faSort = faSort;
    faPlus = faPlus;
    faDownload = faDownload;
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
                const profileInfo = this.profileService.getProfileInfo();
                this.buildPlanLinkTemplate = profileInfo.buildPlanURLTemplate;
                this.localCIEnabled = this.profileService.isProfileActive(PROFILE_LOCALCI);
                this.onlineIdeEnabled = this.profileService.isProfileActive(PROFILE_THEIA);
                // reconnect exercise with course
                this.programmingExercises.forEach((exercise) => {
                    exercise.course = this.course;
                    this.accountService.setAccessRightsForExercise(exercise);
                    this.numberOfResultsOfSolutionParticipation = getAllResultsOfAllSubmissions(exercise.solutionParticipation?.submissions).length;
                    this.numberOfResultsOfTemplateParticipation = getAllResultsOfAllSubmissions(exercise.templateParticipation?.submissions).length;
                    if (exercise.projectKey) {
                        if (exercise.solutionParticipation?.buildPlanId && !this.localCIEnabled) {
                            exercise.solutionParticipation.buildPlanUrl = createBuildPlanUrl(
                                this.buildPlanLinkTemplate!,
                                exercise.projectKey,
                                exercise.solutionParticipation.buildPlanId,
                            );
                        }
                        if (exercise.templateParticipation?.buildPlanId && !this.localCIEnabled) {
                            exercise.templateParticipation.buildPlanUrl = createBuildPlanUrl(
                                this.buildPlanLinkTemplate!,
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
}
