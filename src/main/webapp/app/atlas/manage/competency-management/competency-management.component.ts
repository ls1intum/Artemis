import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, effect, inject, signal, untracked } from '@angular/core';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { CompetencyWithTailRelationDTO, CourseCompetency, CourseCompetencyType, getIcon } from 'app/atlas/shared/entities/competency.model';
import { Subscription, firstValueFrom, map } from 'rxjs';
import { faCircleQuestion, faEdit, faFileImport, faPencilAlt, faPlus, faRobot, faTrash } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { PROFILE_IRIS } from 'app/app.constants';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import {
    ImportAllCourseCompetenciesModalComponent,
    ImportAllCourseCompetenciesResult,
} from 'app/atlas/manage/import-all-course-competencies-modal/import-all-course-competencies-modal.component';
import { CourseCompetencyApiService } from 'app/atlas/shared/services/course-competency-api.service';
import { CompetencyManagementTableComponent } from 'app/atlas/manage/competency-management/competency-management-table.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

import { CourseCompetenciesRelationModalComponent } from 'app/atlas/manage/course-competencies-relation-modal/course-competencies-relation-modal.component';
import { CourseCompetencyExplanationModalComponent } from 'app/atlas/manage/course-competency-explanation-modal/course-competency-explanation-modal.component';
import { AgentChatModalComponent } from 'app/atlas/manage/agent-chat-modal/agent-chat-modal.component';
import { toSignal } from '@angular/core/rxjs-interop';
import { CourseTitleBarTitleComponent } from 'app/core/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseTitleBarTitleDirective } from 'app/core/course/shared/directives/course-title-bar-title.directive';
import { CourseTitleBarActionsDirective } from 'app/core/course/shared/directives/course-title-bar-actions.directive';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { IS_AT_LEAST_INSTRUCTOR } from 'app/shared/constants/authority.constants';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-competency-management',
    templateUrl: './competency-management.component.html',
    styleUrl: './competency-management.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        CompetencyManagementTableComponent,
        TranslateDirective,
        FontAwesomeModule,
        RouterModule,
        CourseTitleBarTitleComponent,
        CourseTitleBarTitleDirective,
        CourseTitleBarActionsDirective,
    ],
})
export class CompetencyManagementComponent implements OnInit, OnDestroy {
    protected readonly faEdit = faEdit;
    protected readonly faPlus = faPlus;
    protected readonly faFileImport = faFileImport;
    protected readonly faTrash = faTrash;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faRobot = faRobot;
    protected readonly faCircleQuestion = faCircleQuestion;

    readonly getIcon = getIcon;
    readonly documentationType: DocumentationType = 'Competencies';
    readonly CourseCompetencyType = CourseCompetencyType;

    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly courseCompetencyApiService = inject(CourseCompetencyApiService);
    private readonly alertService = inject(AlertService);
    private readonly modalService = inject(NgbModal);
    private readonly profileService = inject(ProfileService);
    private readonly irisSettingsService = inject(IrisSettingsService);
    private readonly featureToggleService = inject(FeatureToggleService);
    private readonly sessionStorageService = inject(SessionStorageService);
    private readonly accountService = inject(AccountService);

    readonly courseId = toSignal(this.activatedRoute.parent!.params.pipe(map((params) => Number(params.courseId))), { requireSync: true });
    readonly isLoading = signal<boolean>(false);

    readonly courseCompetencies = signal<CourseCompetency[]>([]);
    competencies = computed(() => this.courseCompetencies().filter((cc) => cc.type === CourseCompetencyType.COMPETENCY));
    prerequisites = computed(() => this.courseCompetencies().filter((cc) => cc.type === CourseCompetencyType.PREREQUISITE));

    irisCompetencyGenerationEnabled = signal<boolean>(false);
    standardizedCompetenciesEnabled = toSignal(this.featureToggleService.getFeatureToggleActive(FeatureToggle.StandardizedCompetencies), { requireSync: true });
    agentChatEnabled = signal<boolean>(false);

    private agentChatSubscription?: Subscription;

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            untracked(async () => await this.loadCourseCompetencies(courseId));
        });
        effect(() => {
            const irisEnabled = this.profileService.isProfileActive(PROFILE_IRIS);
            untracked(async () => {
                if (irisEnabled) {
                    await this.loadIrisEnabled();
                }
            });
        });
    }

    ngOnInit(): void {
        const lastVisit = this.sessionStorageService.retrieve('alreadyVisitedCompetencyManagement');
        if (!lastVisit) {
            this.openCourseCompetencyExplanation();
        }
        this.sessionStorageService.store('alreadyVisitedCompetencyManagement', true);

        this.agentChatSubscription = this.featureToggleService.getFeatureToggleActive(FeatureToggle.AtlasAgent).subscribe((isFeatureEnabled) => {
            const hasAuthority = this.accountService.hasAnyAuthorityDirect(IS_AT_LEAST_INSTRUCTOR);
            this.agentChatEnabled.set(hasAuthority && isFeatureEnabled);
        });
    }

    ngOnDestroy(): void {
        this.agentChatSubscription?.unsubscribe();
    }

    private async loadIrisEnabled() {
        try {
            const combinedCourseSettings = await firstValueFrom(this.irisSettingsService.getCombinedCourseSettings(this.courseId()));
            this.irisCompetencyGenerationEnabled.set(combinedCourseSettings?.irisCompetencyGenerationSettings?.enabled ?? false);
        } catch (error) {
            this.alertService.error(error);
        }
    }

    private async loadCourseCompetencies(courseId: number) {
        try {
            this.isLoading.set(true);
            const courseCompetencies = await this.courseCompetencyApiService.getCourseCompetenciesByCourseId(courseId);
            this.courseCompetencies.set(courseCompetencies);
        } catch (error) {
            this.alertService.error(error);
        } finally {
            this.isLoading.set(false);
        }
    }

    protected openCourseCompetenciesRelationModal(): void {
        const modalRef = this.modalService.open(CourseCompetenciesRelationModalComponent, {
            size: 'xl',
            backdrop: 'static',
            windowClass: 'course-competencies-relation-graph-modal',
        });
        modalRef.componentInstance.courseId = signal<number>(this.courseId());
        modalRef.componentInstance.courseCompetencies = signal<CourseCompetency[]>(this.courseCompetencies());
    }

    /**
     * Opens a modal for selecting a course to import all competencies from.
     */
    async openImportAllModal() {
        const modalRef = this.modalService.open(ImportAllCourseCompetenciesModalComponent, {
            size: 'lg',
            backdrop: 'static',
        });
        modalRef.componentInstance.courseId = signal<number>(this.courseId());
        const importResults: ImportAllCourseCompetenciesResult | undefined = await modalRef.result;
        if (!importResults) {
            return;
        }
        const courseTitle = importResults.course.title ?? '';
        try {
            const importedCompetencies = await this.courseCompetencyApiService.importAllByCourseId(this.courseId(), importResults.courseCompetencyImportOptions);
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
            this.alertService.error(error);
        }
    }

    /**
     * Updates the component with the new data from the importAll modal
     * @param res Array of DTOs containing the new competencies
     * @private
     */
    updateDataAfterImportAll(res: Array<CompetencyWithTailRelationDTO>) {
        const importedCourseCompetencies = res.map((dto) => dto.competency!);
        const newCourseCompetencies = importedCourseCompetencies.filter(
            (competency) => !this.courseCompetencies().some((existingCompetency) => existingCompetency.id === competency.id),
        );
        this.courseCompetencies.update((courseCompetencies) => courseCompetencies.concat(newCourseCompetencies));
    }

    onRemoveCompetency(competencyId: number) {
        this.courseCompetencies.update((courseCompetencies) => courseCompetencies.filter((cc) => cc.id !== competencyId));
    }

    onCompetenciesAdded(added: CourseCompetency[]) {
        // Merge added items into parent-owned list, avoiding duplicates
        const current = this.courseCompetencies();
        const newOnes = added.filter((c) => !current.some((e) => e?.id === c?.id));
        if (newOnes.length) {
            this.courseCompetencies.update((list) => list.concat(newOnes));
        }
    }

    openCourseCompetencyExplanation(): void {
        this.modalService.open(CourseCompetencyExplanationModalComponent, {
            size: 'xl',
            backdrop: 'static',
            windowClass: 'course-competency-explanation-modal',
        });
    }

    /**
     * Opens the Agent Chat Modal for AI-powered competency assistance.
     * Listens for competency changes and refreshes the list immediately.
     */
    protected openAgentChatModal(): void {
        const modalRef = this.modalService.open(AgentChatModalComponent, {
            size: 'lg',
            backdrop: true,
        });
        modalRef.componentInstance.courseId = this.courseId();

        modalRef.componentInstance.competencyChanged.subscribe(() => {
            this.loadCourseCompetencies(this.courseId());
        });
    }
}
