import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { LearningPathsConfigurationComponent } from 'app/course/learning-paths/components/learning-paths-configuration/learning-paths-configuration.component';
import { map } from 'rxjs';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Course } from 'app/entities/course.model';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { LearningPathsStateComponent } from 'app/course/learning-paths/components/learning-paths-state/learning-paths-state.component';
import { LearningPathsTableComponent } from 'app/course/learning-paths/components/learning-paths-table/learning-paths-table.component';

@Component({
    selector: 'jhi-learning-path-instructor-page',
    standalone: true,
    imports: [LearningPathsConfigurationComponent, ArtemisSharedCommonModule, LearningPathsStateComponent, LearningPathsTableComponent],
    templateUrl: './learning-path-instructor-page.component.html',
    styleUrl: './learning-path-instructor-page.component.scss',
})
export class LearningPathInstructorPageComponent {
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly courseStorageService = inject(CourseStorageService);
    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly alertService = inject(AlertService);

    courseId = toSignal(this.activatedRoute.parent!.params.pipe(map((params) => Number(params.courseId))), { requireSync: true });
    private readonly course = toSignal(this.courseStorageService.subscribeToCourseUpdates(this.courseId()));
    // TODO: Change to either call health status or get course from courseStorage (but service does not include info yet)
    readonly learningPathsEnabled = computed(() => this.course()?.learningPathsEnabled ?? true);

    readonly isLoading = signal<boolean>(false);

    protected async enableLearningPaths(): Promise<void> {
        try {
            this.isLoading.set(true);
            await this.learningPathApiService.enableLearningPaths(this.courseId());
            this.courseStorageService.updateCourse(<Course>{ ...this.course(), learningPathsEnabled: true });
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoading.set(false);
        }
    }
}
