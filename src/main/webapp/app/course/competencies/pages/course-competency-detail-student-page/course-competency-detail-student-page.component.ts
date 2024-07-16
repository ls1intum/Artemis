import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { CompetencyJol, CompetencyProgress, ConfidenceReason, CourseCompetency, getMastery, getProgress } from 'app/entities/competency.model';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { CourseCompetencyDetailHeaderComponent } from 'app/course/competencies/components/course-competency-detail-header/course-competency-detail-header.component';
import { CourseCompetencyDetailLectureUnitsComponent } from 'app/course/competencies/components/course-competency-detail-lecture-units/course-competency-detail-lecture-units.component';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { FireworksModule } from 'app/shared/fireworks/fireworks.module';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';

@Component({
    selector: 'jhi-course-competency-detail-student-page',
    standalone: true,
    imports: [
        ArtemisSharedCommonModule,
        CourseCompetencyDetailHeaderComponent,
        CourseCompetencyDetailLectureUnitsComponent,
        ArtemisCompetenciesModule,
        ArtemisSharedComponentModule,
        ArtemisSidePanelModule,
        FireworksModule,
    ],
    templateUrl: './course-competency-detail-student-page.component.html',
})
export class CourseCompetencyDetailStudentPageComponent {
    protected readonly ConfidenceReason = ConfidenceReason;

    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly featureToggleService = inject(FeatureToggleService);
    private readonly courseStorageService = inject(CourseStorageService);
    private readonly alertService = inject(AlertService);

    private readonly courseCompetencyApiService = inject(CourseCompetencyApiService);

    readonly isLoading = signal<boolean>(false);

    readonly courseId = toSignal(this.activatedRoute.parent!.parent!.params.pipe(map((params) => Number(params.courseId))), { requireSync: true });
    readonly course = computed(() => this.courseStorageService.getCourse(this.courseId()));

    private readonly courseCompetencyId = toSignal(this.activatedRoute.params.pipe(map((params) => Number(params.competencyId))), { requireSync: true });
    readonly courseCompetency = signal<CourseCompetency | undefined>(undefined);

    readonly userProgress = computed(() => this.courseCompetency()?.userProgress?.first() ?? <CompetencyProgress>{ progress: 0, confidence: 1 });
    readonly progress = computed(() => getProgress(this.userProgress()));
    readonly mastery = computed(() => getMastery(this.userProgress()));
    readonly isMastered = computed(() => this.mastery() >= 100);

    readonly showFireworks = signal<boolean>(false);

    private readonly dashboardFeatureActive = toSignal(this.featureToggleService.getFeatureToggleActive(FeatureToggle.StudentCourseAnalyticsDashboard), { requireSync: true });

    readonly promptForJolRating = signal<boolean>(false);
    readonly judgementOfLearning = signal<CompetencyJol | undefined>(undefined);
    readonly judgementOfLearningEnabled = computed(() => (this.course()?.studentCourseAnalyticsDashboardEnabled ?? false) && this.dashboardFeatureActive());

    constructor() {
        // Fetch data when the course and course competency id are available
        effect(() => this.loadData(this.courseId(), this.courseCompetencyId()), { allowSignalWrites: true });
    }

    async loadData(courseId: number, courseCompetencyId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            const courseCompetency = await this.courseCompetencyApiService.getCourseCompetencyById(courseId, courseCompetencyId);
            if (this.judgementOfLearningEnabled()) {
                await this.loadPromptJolRating(courseCompetency, courseId, courseCompetencyId);
            }
            this.courseCompetency.set(courseCompetency);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading.set(false);
        }
    }

    private async loadPromptJolRating(courseCompetency: CourseCompetency, courseId: number, competencyId: number): Promise<void> {
        const [competencies, jolResponse] = await Promise.all([
            this.courseCompetencyApiService.getCourseCompetenciesByCourseId(courseId),
            this.courseCompetencyApiService.getJoL(courseId, competencyId),
        ]);
        const competencyProgress = courseCompetency.userProgress?.first();
        this.promptForJolRating.set(CompetencyJol.shouldPromptForJol(courseCompetency, competencyProgress, competencies));
        if (jolResponse.current.competencyProgress === (competencyProgress?.progress ?? 0) || jolResponse.current.competencyConfidence === (competencyProgress?.confidence ?? 1)) {
            this.judgementOfLearning.set(jolResponse.current);
        }
    }

    async onLectureUnitCompletion(): Promise<void> {
        try {
            const competencyProgress = await this.courseCompetencyApiService.getCourseCompetencyProgressById(this.courseId(), this.courseCompetencyId(), true);
            this.courseCompetency.update((courseCompetency) => ({
                ...courseCompetency,
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
