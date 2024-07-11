import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { CompetencyApiService } from 'app/course/competencies/services/competency-api.service';
import { Competency, CompetencyJol, CompetencyProgress, ConfidenceReason, getMastery, getProgress } from 'app/entities/competency.model';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { CompetencyDetailHeaderComponent } from 'app/course/competencies/components/competency-detail-header/competency-detail-header.component';
import { CompetencyDetailLectureUnitsComponent } from 'app/course/competencies/components/competency-detail-lecture-units/competency-detail-lecture-units.component';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { FireworksModule } from 'app/shared/fireworks/fireworks.module';

@Component({
    selector: 'jhi-competency-detail-student-page',
    standalone: true,
    imports: [
        ArtemisSharedCommonModule,
        CompetencyDetailHeaderComponent,
        CompetencyDetailLectureUnitsComponent,
        ArtemisCompetenciesModule,
        ArtemisSharedComponentModule,
        ArtemisSidePanelModule,
        FireworksModule,
    ],
    templateUrl: './competency-detail-student-page.component.html',
})
export class CompetencyDetailStudentPageComponent {
    protected readonly ConfidenceReason = ConfidenceReason;

    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly featureToggleService = inject(FeatureToggleService);
    private readonly courseStorageService = inject(CourseStorageService);
    private readonly competencyApiService = inject(CompetencyApiService);
    private readonly alertService = inject(AlertService);

    readonly isLoading = signal<boolean>(false);

    readonly courseId = toSignal(this.activatedRoute.parent!.parent!.params.pipe(map((params) => Number(params.courseId))), { requireSync: true });
    readonly course = computed(() => this.courseStorageService.getCourse(this.courseId()));

    private readonly competencyId = toSignal(this.activatedRoute.params.pipe(map((params) => Number(params.competencyId))), { requireSync: true });
    readonly competency = signal<Competency | undefined>(undefined);

    readonly userProgress = computed(() => this.competency()?.userProgress?.first() ?? <CompetencyProgress>{ progress: 0, confidence: 1 });
    readonly progress = computed(() => getProgress(this.userProgress()));
    readonly mastery = computed(() => getMastery(this.userProgress()));
    readonly isMastered = computed(() => this.mastery() >= 100);

    readonly showFireworks = signal<boolean>(false);

    private readonly dashboardFeatureActive = toSignal(this.featureToggleService.getFeatureToggleActive(FeatureToggle.StudentCourseAnalyticsDashboard), { requireSync: true });

    readonly promptForJolRating = signal<boolean>(false);
    readonly judgementOfLearning = signal<CompetencyJol | undefined>(undefined);
    readonly judgementOfLearningEnabled = computed(() => (this.course()?.studentCourseAnalyticsDashboardEnabled ?? false) && this.dashboardFeatureActive());

    constructor() {
        // Fetch data when the course and competency id are available
        effect(() => this.loadData(this.courseId(), this.competencyId()), { allowSignalWrites: true });
    }

    private async loadData(courseId: number, competencyId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            const competency = await this.competencyApiService.getCompetencyById(courseId, competencyId);
            if (this.judgementOfLearningEnabled()) {
                await this.loadPromptJolRating(competency, courseId, competencyId);
            }
            this.competency.set(competency);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading.set(false);
        }
    }

    private async loadPromptJolRating(competency: Competency, courseId: number, competencyId: number): Promise<void> {
        const [competencies, jolResponse] = await Promise.all([
            this.competencyApiService.getCompetenciesByCourseId(courseId),
            this.competencyApiService.getJoL(courseId, competencyId),
        ]);
        const competencyProgress = competency.userProgress?.first();
        this.promptForJolRating.set(CompetencyJol.shouldPromptForJol(competency, competencyProgress, competencies));
        if (jolResponse.current.competencyProgress === (competencyProgress?.progress ?? 0) || jolResponse.current.competencyConfidence === (competencyProgress?.confidence ?? 1)) {
            this.judgementOfLearning.set(jolResponse.current);
        }
    }

    async onLectureUnitCompletion(): Promise<void> {
        try {
            const competencyProgress = await this.competencyApiService.getCompetencyProgress(this.courseId(), this.competencyId(), true);
            this.competency.update((competency) => ({
                ...competency,
                userProgress: [competencyProgress],
            }));
            if (this.isMastered()) {
                this.startFireworks();
            }
        } catch (error) {
            onError(this.alertService, error);
        }
    }

    private startFireworks(): void {
        if (!this.showFireworks()) {
            setTimeout(() => this.showFireworks.set(true), 500);
            setTimeout(() => this.showFireworks.set(false), 5000);
        }
    }
}
