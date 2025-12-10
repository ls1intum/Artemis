import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { lastValueFrom, map } from 'rxjs';
import { LearningPathApiService } from 'app/atlas/shared/services/learning-path-api.service';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Course } from 'app/core/course/shared/entities/course.model';
import { LearningPathsStateComponent } from 'app/atlas/manage/learning-paths-state/learning-paths-state.component';
import { LearningPathsTableComponent } from 'app/atlas/manage/learning-paths-table/learning-paths-table.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { LearningPathsAnalyticsComponent } from 'app/atlas/manage/learning-paths-analytics/learning-paths-analytics.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FeatureActivationComponent } from 'app/shared/feature-activation/feature-activation.component';
import { faNetworkWired } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-learning-path-instructor-page',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [LearningPathsStateComponent, LearningPathsTableComponent, LearningPathsAnalyticsComponent, TranslateDirective, FeatureActivationComponent],
    templateUrl: './learning-path-instructor-page.component.html',
    styleUrl: './learning-path-instructor-page.component.scss',
})
export class LearningPathInstructorPageComponent {
    protected readonly faNetworkWired = faNetworkWired;
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly alertService = inject(AlertService);
    private readonly courseManagementService = inject(CourseManagementService);

    readonly courseId = toSignal(this.activatedRoute.parent!.params.pipe(map((params) => Number(params.courseId))), { requireSync: true });
    readonly course = signal<Course | undefined>(undefined);
    readonly learningPathsEnabled = computed(() => this.course()?.learningPathsEnabled ?? false);

    readonly isLoading = signal<boolean>(false);

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            untracked(() => this.loadCourse(courseId));
        });
    }

    private async loadCourse(courseId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            const courseBody = await lastValueFrom(this.courseManagementService.findOneForDashboard(courseId));
            this.course.set(courseBody.body!);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading.set(false);
        }
    }

    async enableLearningPaths(): Promise<void> {
        try {
            this.isLoading.set(true);
            await this.learningPathApiService.enableLearningPaths(this.courseId());
            this.course.update((course) => Object.assign({}, course!, { learningPathsEnabled: true }));
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading.set(false);
        }
    }
}
