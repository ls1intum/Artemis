import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { Competency, CompetencyWithTailRelationDTO, CourseCompetency, CourseCompetencyType, getIcon } from 'app/entities/competency.model';
import { onError } from 'app/shared/util/global.utils';
import { Subject, Subscription } from 'rxjs';
import { faEdit, faFileImport, faPencilAlt, faPlus, faRobot, faTrash } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { PROFILE_IRIS } from 'app/app.constants';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { Prerequisite } from 'app/entities/prerequisite.model';
import {
    ImportAllCourseCompetenciesModalComponent,
    ImportAllCourseCompetenciesResult,
} from 'app/course/competencies/components/import-all-course-competencies-modal/import-all-course-competencies-modal.component';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';
import { CompetencyManagementTableComponent } from 'app/course/competencies/competency-management/competency-management-table.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { CourseCompetenciesRelationModalComponent } from 'app/course/competencies/components/course-competencies-relation-modal/course-competencies-relation-modal.component';

@Component({
    selector: 'jhi-competency-management',
    templateUrl: './competency-management.component.html',
    standalone: true,
    imports: [CompetencyManagementTableComponent, TranslateDirective, FontAwesomeModule, RouterModule, ArtemisSharedComponentModule],
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

    // Icons
    protected readonly faEdit = faEdit;
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
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly courseCompetencyApiService = inject(CourseCompetencyApiService);
    private readonly alertService = inject(AlertService);
    private readonly modalService = inject(NgbModal);
    private readonly profileService = inject(ProfileService);
    private readonly irisSettingsService = inject(IrisSettingsService);
    private readonly featureToggleService = inject(FeatureToggleService);

    ngOnInit(): void {
        this.activatedRoute.parent!.params.subscribe(async (params) => {
            this.courseId = Number(params['courseId']);
            await this.loadData();
            this.loadIrisEnabled();
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
     * Loads all data for the competency management: Prerequisites, competencies (with average course progress)
     */
    async loadData() {
        try {
            this.isLoading = true;
            this.courseCompetencies = await this.courseCompetencyApiService.getCourseCompetenciesByCourseId(this.courseId);
            this.competencies = this.courseCompetencies.filter((competency) => competency.type === CourseCompetencyType.COMPETENCY);
            this.prerequisites = this.courseCompetencies.filter((competency) => competency.type === CourseCompetencyType.PREREQUISITE);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading = false;
        }
    }

    protected openCourseCompetenciesRelationModal(): void {
        const modalRef = this.modalService.open(CourseCompetenciesRelationModalComponent, {
            size: 'xl',
            backdrop: 'static',
            windowClass: 'course-competencies-relation-graph-modal',
        });
        modalRef.componentInstance.courseId = signal<number>(this.courseId);
        modalRef.componentInstance.courseCompetencies = signal<CourseCompetency[]>(this.courseCompetencies);
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
        const importResults: ImportAllCourseCompetenciesResult | undefined = await modalRef.result;
        if (!importResults) {
            return;
        }
        const courseTitle = importResults.course.title ?? '';
        try {
            const importedCompetencies = await this.courseCompetencyApiService.importAllByCourseId(this.courseId, importResults.courseCompetencyImportOptions);
            if (importedCompetencies.length) {
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
     * Updates the component with the new data from the importAll modal
     * @param res Array of DTOs containing the new competencies
     * @private
     */
    updateDataAfterImportAll(res: Array<CompetencyWithTailRelationDTO>) {
        const importedCompetencies = res.map((dto) => dto.competency).filter((element): element is Competency => element?.type === CourseCompetencyType.COMPETENCY);
        const importedPrerequisites = res.map((dto) => dto.competency).filter((element): element is Prerequisite => element?.type === CourseCompetencyType.PREREQUISITE);

        this.competencies = this.competencies.concat(importedCompetencies);
        this.prerequisites = this.prerequisites.concat(importedPrerequisites);
        this.courseCompetencies = this.competencies.concat(this.prerequisites);
    }

    onRemoveCompetency(competencyId: number) {
        this.competencies = this.competencies.filter((competency) => competency.id !== competencyId);
        this.prerequisites = this.prerequisites.filter((prerequisite) => prerequisite.id !== competencyId);
        this.courseCompetencies = this.competencies.concat(this.prerequisites);
    }
}
