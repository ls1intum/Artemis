import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { AlertService } from 'app/core/util/alert.service';
import {
    Competency,
    CompetencyRelation,
    CompetencyRelationDTO,
    CompetencyWithTailRelationDTO,
    CourseCompetencyProgress,
    dtoToCompetencyRelation,
    getIcon,
} from 'app/entities/competency.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { filter, finalize, map, switchMap } from 'rxjs/operators';
import { onError } from 'app/shared/util/global.utils';
import { Subject, forkJoin } from 'rxjs';
import { faFileImport, faPencilAlt, faPlus, faRobot, faTrash } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { PrerequisiteImportComponent } from 'app/course/competencies/competency-management/prerequisite-import.component';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { CompetencyImportCourseComponent, ImportAllFromCourseResult } from 'app/course/competencies/competency-management/competency-import-course.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { PROFILE_IRIS } from 'app/app.constants';

@Component({
    selector: 'jhi-competency-management',
    templateUrl: './competency-management.component.html',
})
export class CompetencyManagementComponent implements OnInit, OnDestroy {
    courseId: number;
    isLoading = false;
    irisCompetencyGenerationEnabled = false;
    private dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();

    competencies: Competency[] = [];
    prerequisites: Competency[] = [];
    relations: CompetencyRelation[] = [];

    // Icons
    protected readonly faPlus = faPlus;
    protected readonly faFileImport = faFileImport;
    protected readonly faTrash = faTrash;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faRobot = faRobot;

    //other constants
    readonly getIcon = getIcon;
    readonly documentationType: DocumentationType = 'Competencies';

    constructor(
        private activatedRoute: ActivatedRoute,
        private competencyService: CompetencyService,
        private alertService: AlertService,
        private modalService: NgbModal,
        private profileService: ProfileService,
        private irisSettingsService: IrisSettingsService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.parent!.params.subscribe((params) => {
            this.courseId = params['courseId'];
            if (this.courseId) {
                this.loadData();
                this.loadIrisEnabled();
            }
        });
    }

    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Delete a competency (and its relations)
     *
     * @param competencyId the id of the competency
     */
    deleteCompetency(competencyId: number) {
        this.competencyService.delete(competencyId, this.courseId).subscribe({
            next: () => {
                this.competencies = this.competencies.filter((competency) => competency.id !== competencyId);
                this.relations = this.relations.filter((relation) => relation.tailCompetency?.id !== competencyId && relation.headCompetency?.id !== competencyId);
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Remove a prerequisite from the course
     *
     * @param competencyId the id of the prerequisite
     */
    removePrerequisite(competencyId: number) {
        this.competencyService.removePrerequisite(competencyId, this.courseId).subscribe({
            next: () => {
                const index = this.prerequisites.findIndex((prerequisite) => prerequisite.id === competencyId);
                this.prerequisites.splice(index, 1);
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * Sends a request to determine if Iris and Competency Generation is enabled
     *
     * @private
     */
    private loadIrisEnabled() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            const irisEnabled = profileInfo.activeProfiles.includes(PROFILE_IRIS);
            if (irisEnabled) {
                this.irisSettingsService.getCombinedCourseSettings(this.courseId).subscribe((settings) => {
                    this.irisCompetencyGenerationEnabled = settings?.irisCompetencyGenerationSettings?.enabled ?? false;
                });
            }
        });
    }

    /**
     * Loads all data for the competency management: Prerequisites, competencies (with average course progress) and competency relations
     */
    loadData() {
        this.isLoading = true;
        this.competencyService
            .getAllPrerequisitesForCourse(this.courseId)
            .pipe(map((response: HttpResponse<Competency[]>) => response.body!))
            .subscribe({
                next: (competencies) => {
                    this.prerequisites = competencies;
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        this.competencyService
            .getAllForCourse(this.courseId)
            .pipe(
                switchMap((res) => {
                    this.competencies = res.body!;

                    const relationsObservable = this.competencyService.getCompetencyRelations(this.courseId);

                    const progressObservable = this.competencies.map((lg) => {
                        return this.competencyService.getCourseProgress(lg.id!, this.courseId);
                    });

                    return forkJoin([relationsObservable, forkJoin(progressObservable)]);
                }),
            )
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: ([competencyRelations, competencyProgressResponses]) => {
                    this.relations = (competencyRelations.body ?? []).map((relationDTO) => dtoToCompetencyRelation(relationDTO));

                    for (const competencyProgressResponse of competencyProgressResponses) {
                        const courseCompetencyProgress: CourseCompetencyProgress = competencyProgressResponse.body!;
                        this.competencies.find((competency) => competency.id === courseCompetencyProgress.competencyId)!.courseProgress = courseCompetencyProgress;
                    }
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    /**
     * Opens a modal for adding a prerequisite to the current course.
     */
    openPrerequisiteSelectionModal() {
        const modalRef = this.modalService.open(PrerequisiteImportComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.disabledIds = this.competencies.concat(this.prerequisites).map((competency) => competency.id);
        modalRef.result.then((result: Competency) => {
            this.competencyService
                .addPrerequisite(result.id!, this.courseId)
                .pipe(
                    filter((res: HttpResponse<Competency>) => res.ok),
                    map((res: HttpResponse<Competency>) => res.body),
                )
                .subscribe({
                    next: (res: Competency) => {
                        this.prerequisites.push(res);
                    },
                    error: (res: HttpErrorResponse) => onError(this.alertService, res),
                });
        });
    }

    /**
     * Opens a modal for selecting a course to import all competencies from.
     */
    openImportAllModal() {
        const modalRef = this.modalService.open(CompetencyImportCourseComponent, { size: 'lg', backdrop: 'static' });
        //unary operator is necessary as otherwise courseId is seen as a string and will not match.
        modalRef.componentInstance.disabledIds = [+this.courseId];
        modalRef.result.then((result: ImportAllFromCourseResult) => {
            const courseTitle = result.courseForImportDTO.title ?? '';
            this.competencyService
                .importAll(this.courseId, result.courseForImportDTO.id!, result.importRelations)
                .pipe(
                    filter((res: HttpResponse<Array<CompetencyWithTailRelationDTO>>) => res.ok),
                    map((res: HttpResponse<Array<CompetencyWithTailRelationDTO>>) => res.body),
                )
                .subscribe({
                    next: (res: Array<CompetencyWithTailRelationDTO>) => {
                        if (res.length > 0) {
                            this.alertService.success('artemisApp.competency.importAll.success', { noOfCompetencies: res.length, courseTitle: courseTitle });
                            this.updateDataAfterImportAll(res);
                        } else {
                            this.alertService.warning('artemisApp.competency.importAll.warning', { courseTitle: courseTitle });
                        }
                    },
                    error: (res: HttpErrorResponse) => onError(this.alertService, res),
                });
        });
    }

    /**
     * Updates the component and its relation chart with the new data from the importAll modal
     * @param res Array of DTOs containing the new competencies and relations
     * @private
     */
    updateDataAfterImportAll(res: Array<CompetencyWithTailRelationDTO>) {
        const importedCompetencies = res.map((dto) => dto.competency).filter((element): element is Competency => !!element);
        const importedRelations = res
            .map((dto) => dto.tailRelations)
            .flat()
            .filter((element): element is CompetencyRelationDTO => !!element)
            .map((dto) => dtoToCompetencyRelation(dto));

        this.competencies = this.competencies.concat(importedCompetencies);
        this.relations = this.relations.concat(importedRelations);
    }

    /**
     * creates a given competency relation
     *
     * @param relation the given competency relation
     */
    createRelation(relation: CompetencyRelation) {
        this.competencyService
            .createCompetencyRelation(relation, this.courseId)
            .pipe(
                filter((res: HttpResponse<CompetencyRelation>) => res.ok),
                map((res: HttpResponse<CompetencyRelation>) => res.body),
            )
            .subscribe({
                next: (relation) => {
                    if (relation) {
                        this.relations = this.relations.concat(relation);
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    /**
     * deletes a competency relation with the given id
     *
     * @param relationId the given id
     */
    removeRelation(relationId: number) {
        this.competencyService.removeCompetencyRelation(relationId, this.courseId).subscribe({
            next: () => {
                this.relations = this.relations.filter((relation) => relation.id !== relationId);
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }
}
