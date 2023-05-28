import { Component, ContentChild, Input, OnDestroy, OnInit, TemplateRef } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseInstructorRepositoryType, ProgrammingExerciseService } from './services/programming-exercise.service';
import { ActivatedRoute, Router } from '@angular/router';
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
import { ProgrammingExerciseParticipationType } from 'app/entities/programming-exercise-participation.model';
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
    faTimes,
    faUsers,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { ExerciseImportWrapperComponent } from 'app/exercises/shared/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { PROFILE_LOCALVC } from 'app/app.constants';

@Component({
    selector: 'jhi-programming-exercise',
    templateUrl: './programming-exercise.component.html',
})
export class ProgrammingExerciseComponent extends ExerciseComponent implements OnInit, OnDestroy {
    @Input() programmingExercises: ProgrammingExercise[];
    filteredProgrammingExercises: ProgrammingExercise[];
    selectedProgrammingExercises: ProgrammingExercise[];
    readonly ActionType = ActionType;
    FeatureToggle = FeatureToggle;
    solutionParticipationType = ProgrammingExerciseParticipationType.SOLUTION;
    templateParticipationType = ProgrammingExerciseParticipationType.TEMPLATE;
    allChecked = false;
    // Used to make the repository links download the repositories instead of linking to Bitbucket/GitLab.
    localVCEnabled = false;

    // extension points, see shared/extension-point
    @ContentChild('overrideGenerateAndImportButton') overrideGenerateAndImportButton: TemplateRef<any>;
    @ContentChild('overrideRepositoryAndBuildPlan') overrideRepositoryAndBuildPlan: TemplateRef<any>;
    @ContentChild('overrideButtons') overrideButtons: TemplateRef<any>;
    private buildPlanLinkTemplate: string;

    // Icons
    faSort = faSort;
    faPlus = faPlus;
    faDownload = faDownload;
    faTimes = faTimes;
    faBook = faBook;
    faWrench = faWrench;
    faCheckDouble = faCheckDouble;
    faUsers = faUsers;
    faTable = faTable;
    faListAlt = faListAlt;
    faLightbulb = faLightbulb;
    faPencilAlt = faPencilAlt;
    faFileSignature = faFileSignature;

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private courseExerciseService: CourseExerciseService,
        public exerciseService: ExerciseService,
        private accountService: AccountService,
        private alertService: AlertService,
        private modalService: NgbModal,
        private router: Router,
        private sortService: SortService,
        private profileService: ProfileService,
        courseService: CourseManagementService,
        translateService: TranslateService,
        eventManager: EventManager,
        route: ActivatedRoute,
    ) {
        super(courseService, translateService, route, eventManager);
        this.programmingExercises = [];
        this.selectedProgrammingExercises = [];
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
                });
                // reconnect exercise with course
                this.programmingExercises.forEach((exercise) => {
                    exercise.course = this.course;
                    this.accountService.setAccessRightsForExercise(exercise);
                    if (exercise.projectKey) {
                        if (exercise.solutionParticipation!.buildPlanId) {
                            exercise.solutionParticipation!.buildPlanUrl = createBuildPlanUrl(
                                this.buildPlanLinkTemplate,
                                exercise.projectKey,
                                exercise.solutionParticipation!.buildPlanId,
                            );
                        }
                        if (exercise.templateParticipation!.buildPlanId) {
                            exercise.templateParticipation!.buildPlanUrl = createBuildPlanUrl(
                                this.buildPlanLinkTemplate,
                                exercise.projectKey,
                                exercise.templateParticipation!.buildPlanId,
                            );
                        }
                    }
                    this.selectedProgrammingExercises = [];
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

    protected getChangeEventName(): string {
        return 'programmingExerciseListModification';
    }

    sortRows() {
        this.sortService.sortByProperty(this.programmingExercises, this.predicate, this.reverse);
        this.applyFilter();
    }

    openImportModal() {
        const modalRef = this.modalService.open(ExerciseImportWrapperComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseType = ExerciseType.PROGRAMMING;
        modalRef.result.then((result: ProgrammingExercise) => {
            //when the file is uploaded we set the id to undefined.
            if (result.id === undefined) {
                this.router.navigate(['course-management', this.courseId, 'programming-exercises', 'import-from-file'], {
                    state: {
                        programmingExerciseForImportFromFile: result,
                    },
                });
            } else {
                this.router.navigate(['course-management', this.courseId, 'programming-exercises', 'import', result.id]);
            }
        });
    }

    toggleProgrammingExercise(programmingExercise: ProgrammingExercise) {
        const programmingExerciseIndex = this.selectedProgrammingExercises.indexOf(programmingExercise);
        if (programmingExerciseIndex !== -1) {
            this.selectedProgrammingExercises.splice(programmingExerciseIndex, 1);
        } else {
            this.selectedProgrammingExercises.push(programmingExercise);
        }
    }

    toggleAllProgrammingExercises() {
        this.selectedProgrammingExercises = [];
        if (!this.allChecked) {
            this.selectedProgrammingExercises = this.selectedProgrammingExercises.concat(this.programmingExercises);
        }
        this.allChecked = !this.allChecked;
    }

    isExerciseSelected(programmingExercise: ProgrammingExercise) {
        return this.selectedProgrammingExercises.includes(programmingExercise);
    }

    openEditSelectedModal() {
        const modalRef = this.modalService.open(ProgrammingExerciseEditSelectedComponent, {
            size: 'xl',
            backdrop: 'static',
        });
        modalRef.componentInstance.selectedProgrammingExercises = this.selectedProgrammingExercises;
        modalRef.closed.subscribe(() => {
            location.reload();
        });
    }

    /**
     * Opens modal and executes a consistency check for the selected exercises
     */
    checkConsistencies() {
        const modalRef = this.modalService.open(ConsistencyCheckComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.exercisesToCheck = this.selectedProgrammingExercises;
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
