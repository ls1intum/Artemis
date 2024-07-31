import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { LearningPathsConfigurationComponent } from 'app/course/learning-paths/components/learning-paths-configuration/learning-paths-configuration.component';
import { lastValueFrom, map } from 'rxjs';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Course } from 'app/entities/course.model';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { LearningPathsStateComponent } from 'app/course/learning-paths/components/learning-paths-state/learning-paths-state.component';
import { LearningPathsTableComponent } from 'app/course/learning-paths/components/learning-paths-table/learning-paths-table.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { LearningPathsAnalyticsComponent } from 'app/course/learning-paths/components/learning-paths-analytics/learning-paths-analytics.component';

@Component({
    selector: 'jhi-learning-path-instructor-page',
    standalone: true,
    imports: [LearningPathsConfigurationComponent, ArtemisSharedCommonModule, LearningPathsStateComponent, LearningPathsTableComponent, LearningPathsAnalyticsComponent],
    templateUrl: './learning-path-instructor-page.component.html',
    styleUrl: './learning-path-instructor-page.component.scss',
})
export class LearningPathInstructorPageComponent {
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly alertService = inject(AlertService);
    private readonly courseManagementService = inject(CourseManagementService);

    readonly courseId = toSignal(this.activatedRoute.parent!.params.pipe(map((params) => Number(params.courseId))), { requireSync: true });
    private readonly course = signal<Course | undefined>(undefined);
    readonly learningPathsEnabled = computed(() => this.course()?.learningPathsEnabled ?? false);

    readonly isLoading = signal<boolean>(false);

    constructor() {
        effect(() => this.loadCourse(this.courseId()), { allowSignalWrites: true });
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

    protected async enableLearningPaths(): Promise<void> {
        try {
            this.isLoading.set(true);
            await this.learningPathApiService.enableLearningPaths(this.courseId());
            this.course.update((course) => ({ ...course!, learningPathsEnabled: true }));
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading.set(false);
        }
    }
}
