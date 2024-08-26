import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import {
    Competency,
    CompetencyRelation,
    CompetencyRelationDTO,
    CompetencyWithTailRelationDTO,
    CourseCompetency,
    CourseCompetencyType,
    dtoToCompetencyRelation,
    getIcon,
} from 'app/entities/competency.model';
import { onError } from 'app/shared/util/global.utils';
import { Subject, Subscription } from 'rxjs';
import { faFileImport, faPencilAlt, faPlus, faRobot, faTrash } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { PROFILE_IRIS } from 'app/app.constants';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal.component';
import { TranslateService } from '@ngx-translate/core';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { Prerequisite } from 'app/entities/prerequisite.model';
import {
    ImportAllCourseCompetenciesModalComponent,
    ImportAllCourseCompetenciesResult,
} from 'app/course/competencies/components/import-all-course-competencies-modal/import-all-course-competencies-modal.component';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';

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
    readonly CourseCompetencyType = CourseCompetencyType;

    // Injected services
    private readonly activatedRoute: ActivatedRoute = inject(ActivatedRoute);
    private readonly courseCompetencyApiService: CourseCompetencyApiService = inject(CourseCompetencyApiService);
    private readonly alertService: AlertService = inject(AlertService);
    private readonly modalService: NgbModal = inject(NgbModal);
    private readonly profileService: ProfileService = inject(ProfileService);
    private readonly irisSettingsService: IrisSettingsService = inject(IrisSettingsService);
    private readonly translateService: TranslateService = inject(TranslateService);
    private readonly featureToggleService: FeatureToggleService = inject(FeatureToggleService);

    ngOnInit(): void {
        this.activatedRoute.parent!.params.subscribe(async (params) => {
            this.courseId = params['courseId'];
            if (this.courseId) {
                await this.loadData();
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
    async loadData() {
        try {
            this.isLoading = true;
            this.relations = await this.courseCompetencyApiService.getCourseCompetencyRelations(this.courseId);
            this.courseCompetencies = await this.courseCompetencyApiService.getCourseCompetenciesByCourseId(this.courseId);
            this.competencies = this.courseCompetencies.filter((competency) => competency.type === CourseCompetencyType.COMPETENCY);
            this.prerequisites = this.courseCompetencies.filter((competency) => competency.type === CourseCompetencyType.PREREQUISITE);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading = false;
        }
    }

    /**
     * Opens a modal for selecting a course to import all competencies from.
     */
    async openImportAllModal() {
        const modalRef = this.modalService.open(ImportAllCourseCompetenciesModalComponent, {
            size: 'lg',
            backdrop: 'static',
        });
        modalRef.componentInstance.courseId = signal<number>(this.courseId);
        const importResults: ImportAllCourseCompetenciesResult = await modalRef.result;
        const courseTitle = importResults.course.title ?? '';
        try {
            const importedCompetencies = await this.courseCompetencyApiService.importAllByCourseId(this.courseId, importResults.courseCompetencyImportOptions);
            if (importedCompetencies.length > 0) {
                this.alertService.success(`artemisApp.courseCompetency.importAll.success`, {
                    noOfCompetencies: importedCompetencies.length,
                    courseTitle: courseTitle,
                });
                this.updateDataAfterImportAll(importedCompetencies);
            } else {
                this.alertService.warning(`artemisApp.courseCompetency.importAll.warning`, { courseTitle: courseTitle });
            }
        } catch (error) {
            onError(this.alertService, error);
        }
    }

    /**
     * Updates the component and its relation chart with the new data from the importAll modal
     * @param res Array of DTOs containing the new competencies and relations
     * @private
     */
    updateDataAfterImportAll(res: Array<CompetencyWithTailRelationDTO>) {
        const importedCompetencies = res.map((dto) => dto.competency).filter((element): element is Competency => element?.type === CourseCompetencyType.COMPETENCY);
        const importedPrerequisites = res.map((dto) => dto.competency).filter((element): element is Prerequisite => element?.type === CourseCompetencyType.PREREQUISITE);
        const importedRelations = res
            .map((dto) => dto.tailRelations)
            .flat()
            .filter((element): element is CompetencyRelationDTO => !!element)
            .map((dto) => dtoToCompetencyRelation(dto));

        this.competencies = this.competencies.concat(importedCompetencies);
        this.prerequisites = this.prerequisites.concat(importedPrerequisites);
        this.courseCompetencies = this.competencies.concat(this.prerequisites);
        this.relations = this.relations.concat(importedRelations);
    }

    /**
     * creates a given competency relation
     *
     * @param relation the given competency relation
     */
    async createRelation(relation: CompetencyRelation) {
        try {
            const createdRelation = await this.courseCompetencyApiService.createCourseCompetencyRelation(this.courseId, relation);
            this.relations = this.relations.concat(dtoToCompetencyRelation(createdRelation));
        } catch (error) {
            onError(this.alertService, error);
        }
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
        const titleHead = this.courseCompetencies.find((competency) => competency.id === headId)?.title ?? '';
        const titleTail = this.courseCompetencies.find((competency) => competency.id === tailId)?.title ?? '';

        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'md' });
        modalRef.componentInstance.title = 'artemisApp.competency.manage.deleteRelationModalTitle';
        modalRef.componentInstance.text = this.translateService.instant('artemisApp.competency.manage.deleteRelationModalText', {
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
    private async removeRelation(relationId: number) {
        try {
            await this.courseCompetencyApiService.deleteCourseCompetencyRelation(this.courseId, relationId);
            this.relations = this.relations.filter((relation) => relation.id !== relationId);
        } catch (error) {
            onError(this.alertService, error);
        }
    }

    onRemoveCompetency(competencyId: number) {
        this.competencies = this.competencies.filter((competency) => competency.id !== competencyId);
        this.prerequisites = this.prerequisites.filter((prerequisite) => prerequisite.id !== competencyId);
        this.relations = this.relations.filter((relation) => relation.tailCompetency?.id !== competencyId && relation.headCompetency?.id !== competencyId);
        this.courseCompetencies = this.competencies.concat(this.prerequisites);
    }
}
