import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { AlertService } from 'app/core/util/alert.service';
import {
    Competency,
    CompetencyRelation,
    CompetencyRelationDTO,
    CompetencyWithTailRelationDTO,
    CourseCompetency,
    CourseCompetencyProgress,
    dtoToCompetencyRelation,
    getIcon,
} from 'app/entities/competency.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { filter, map, switchMap } from 'rxjs/operators';
import { onError } from 'app/shared/util/global.utils';
import { Subject, Subscription, forkJoin, of } from 'rxjs';
import { faFileImport, faPencilAlt, faPlus, faRobot, faTrash } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ImportAllCompetenciesComponent, ImportAllFromCourseResult } from 'app/course/competencies/competency-management/import-all-competencies.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { PROFILE_IRIS } from 'app/app.constants';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal.component';
import { TranslateService } from '@ngx-translate/core';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';

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
    standardizedCompetenciesEnabled = false;
    private standardizedCompetencySubscription: Subscription;

    competencies: Competency[] = [];
    prerequisites: Prerequisite[] = [];
    courseCompetencies: CourseCompetency[] = [];
    relations: CompetencyRelation[] = [];

    // Icons
    protected readonly faPlus = faPlus;
    protected readonly faFileImport = faFileImport;
    protected readonly faTrash = faTrash;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faRobot = faRobot;

    // other constants
    readonly getIcon = getIcon;
    readonly documentationType: DocumentationType = 'Competencies';

    // Injected services
    private readonly activatedRoute: ActivatedRoute = inject(ActivatedRoute);
    private readonly competencyService: CompetencyService = inject(CompetencyService);
    private readonly prerequisiteService: PrerequisiteService = inject(PrerequisiteService);
    private readonly alertService: AlertService = inject(AlertService);
    private readonly modalService: NgbModal = inject(NgbModal);
    private readonly profileService: ProfileService = inject(ProfileService);
    private readonly irisSettingsService: IrisSettingsService = inject(IrisSettingsService);
    private readonly translateService: TranslateService = inject(TranslateService);
    private readonly featureToggleService: FeatureToggleService = inject(FeatureToggleService);

    ngOnInit(): void {
        this.activatedRoute.parent!.params.subscribe((params) => {
            this.courseId = params['courseId'];
            if (this.courseId) {
                this.loadData();
                this.loadIrisEnabled();
            }
        });
        this.standardizedCompetencySubscription = this.featureToggleService.getFeatureToggleActive(FeatureToggle.StandardizedCompetencies).subscribe((isActive) => {
            this.standardizedCompetenciesEnabled = isActive;
        });
    }

    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
        if (this.standardizedCompetencySubscription) {
            this.standardizedCompetencySubscription.unsubscribe();
        }
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
                this.courseCompetencies = [...this.competencies, ...this.prerequisites];
                this.relations = this.relations.filter((relation) => relation.tailCompetency?.id !== competencyId && relation.headCompetency?.id !== competencyId);
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Deletes a prerequisite from the course
     *
     * @param prerequisiteId the id of the prerequisite
     */
    deletePrerequisite(prerequisiteId: number) {
        this.prerequisiteService.deletePrerequisite(prerequisiteId, this.courseId).subscribe({
            next: () => {
                this.alertService.success('artemisApp.prerequisite.manage.deleted');
                this.prerequisites = this.prerequisites.filter((prerequisite) => prerequisite.id !== prerequisiteId);
                this.courseCompetencies = [...this.competencies, ...this.prerequisites];
                this.dialogErrorSource.next('');
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
        const relationsObservable = this.competencyService.getCompetencyRelations(this.courseId);
        const prerequisitesObservable = this.prerequisiteService.getAllPrerequisitesForCourse(this.courseId);
        const competencyProgressObservable = this.competencyService.getAllForCourse(this.courseId).pipe(
            switchMap((res) => {
                if (!res.body || res.body.length === 0) {
                    // return observable with empty array as an empty forkJoin never emits a value, causing infinite loading
                    return of([]);
                }
                this.competencies = res.body;

                const progressObservable = this.competencies.map((lg) => {
                    return this.competencyService.getCourseProgress(lg.id!, this.courseId);
                });

                return forkJoin(progressObservable);
            }),
        );
        forkJoin([relationsObservable, prerequisitesObservable, competencyProgressObservable]).subscribe({
            next: ([competencyRelations, prerequisites, competencyProgressResponses]) => {
                this.prerequisites = prerequisites;
                this.courseCompetencies = [...this.competencies, ...this.prerequisites];
                this.relations = (competencyRelations.body ?? []).map((relationDTO) => dtoToCompetencyRelation(relationDTO));

                for (const competencyProgressResponse of competencyProgressResponses) {
                    const courseCompetencyProgress: CourseCompetencyProgress = competencyProgressResponse.body!;
                    this.competencies.find((competency) => competency.id === courseCompetencyProgress.competencyId)!.courseProgress = courseCompetencyProgress;
                }
                this.isLoading = false;
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    /**
     * Opens a modal for selecting a course to import all competencies from.
     */
    openImportAllModal() {
        const modalRef = this.modalService.open(ImportAllCompetenciesComponent, { size: 'lg', backdrop: 'static' });
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
        this.courseCompetencies = [...this.competencies, ...this.prerequisites];
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
                filter((res) => res.ok),
                map((res) => res.body),
            )
            .subscribe({
                next: (relation) => {
                    if (relation) {
                        this.relations = this.relations.concat(dtoToCompetencyRelation(relation));
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    /**
     * Opens a confirmation dialog and if confirmed, deletes a competency relation with the given id
     *
     * @param relationId the given id
     */
    onRemoveRelation(relationId: number) {
        const relation = this.relations.find((relation) => relation.id === relationId);
        const headId = relation?.headCompetency?.id;
        const tailId = relation?.tailCompetency?.id;
        const titleHead = this.competencies.find((competency) => competency.id === headId)?.title ?? '';
        const titleTail = this.competencies.find((competency) => competency.id === tailId)?.title ?? '';

        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'md' });
        modalRef.componentInstance.title = 'artemisApp.competency.manageCompetencies.deleteRelationModalTitle';
        modalRef.componentInstance.text = this.translateService.instant('artemisApp.competency.manageCompetencies.deleteRelationModalText', {
            titleTail: titleTail,
            titleHead: titleHead,
        });
        modalRef.result.then(() => this.removeRelation(relationId));
    }

    /**
     * deletes a competency relation with the given id
     *
     * @param relationId the given id
     */
    private removeRelation(relationId: number) {
        this.competencyService.removeCompetencyRelation(relationId, this.courseId).subscribe({
            next: () => {
                this.relations = this.relations.filter((relation) => relation.id !== relationId);
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }
}
