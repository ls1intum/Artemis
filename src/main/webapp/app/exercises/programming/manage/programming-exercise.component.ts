import { Component, ContentChild, Input, OnDestroy, OnInit, TemplateRef } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { merge } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseInstructorRepositoryType, ProgrammingExerciseService } from './services/programming-exercise.service';
import { ActivatedRoute } from '@angular/router';
import { ExerciseComponent } from 'app/exercises/shared/exercise/exercise.component';
import { TranslateService } from '@ngx-translate/core';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { onError } from 'app/shared/util/global.utils';
import { AccountService } from 'app/core/auth/account.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { SortService } from 'app/shared/service/sort.service';
import { ProgrammingExerciseEditSelectedComponent } from 'app/exercises/programming/manage/programming-exercise-edit-selected.component';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming/programming-exercise-participation.model';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { createBuildPlanUrl } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ConsistencyCheckComponent } from 'app/shared/consistency-check/consistency-check.component';
import {
    faBook,
    faCheckDouble,
    faDownload,
    faFileSignature,
    faLightbulb,
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
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { PROFILE_LOCALCI, PROFILE_LOCALVC, PROFILE_THEIA } from 'app/app.constants';

@Component({
    selector: 'jhi-programming-exercise',
    templateUrl: './programming-exercise.component.html',
})
export class ProgrammingExerciseComponent extends ExerciseComponent implements OnInit, OnDestroy {
    @Input() programmingExercises: ProgrammingExercise[];
    filteredProgrammingExercises: ProgrammingExercise[];
    readonly ActionType = ActionType;
    FeatureToggle = FeatureToggle;
    solutionParticipationType = ProgrammingExerciseParticipationType.SOLUTION;
    templateParticipationType = ProgrammingExerciseParticipationType.TEMPLATE;
    // Used to make the repository links download the repositories instead of linking to GitLab.
    localVCEnabled = false;
    localCIEnabled = false;
    onlineIdeEnabled = false;

    // extension points, see shared/extension-point
    @ContentChild('overrideRepositoryAndBuildPlan') overrideRepositoryAndBuildPlan: TemplateRef<any>;
    @ContentChild('overrideButtons') overrideButtons: TemplateRef<any>;
    private buildPlanLinkTemplate: string;

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
    faLightbulb = faLightbulb;
    faPencilAlt = faPencilAlt;
    faFileSignature = faFileSignature;

    protected get exercises() {
        return this.programmingExercises;
    }

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private courseExerciseService: CourseExerciseService,
        public exerciseService: ExerciseService,
        private accountService: AccountService,
        private alertService: AlertService,
        private modalService: NgbModal,
        private sortService: SortService,
        private profileService: ProfileService,
        courseService: CourseManagementService,
        translateService: TranslateService,
        eventManager: EventManager,
        route: ActivatedRoute,
    ) {
        super(courseService, translateService, route, eventManager);
        this.programmingExercises = [];
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

    trackId(index: number, item: ProgrammingExercise) {
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
    downloadRepository(programmingExerciseId: number | undefined, repositoryType: ProgrammingExerciseInstructorRepositoryType) {
        if (programmingExerciseId) {
            // Repository type cannot be 'AUXILIARY' as auxiliary repositories are currently not supported for the local VCS.
            this.programmingExerciseService.exportInstructorRepository(programmingExerciseId, repositoryType, undefined).subscribe((response: HttpResponse<Blob>) => {
                downloadZipFileFromResponse(response);
                this.alertService.success('artemisApp.programmingExercise.export.successMessageRepos');
            });
        }
    }
}
