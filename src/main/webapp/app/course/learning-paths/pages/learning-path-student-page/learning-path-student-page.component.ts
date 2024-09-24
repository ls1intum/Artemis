import { Component, effect, inject, signal } from '@angular/core';
import { LearningObjectType, LearningPathDTO } from 'app/entities/competency/learning-path.model';
import { map } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { LearningPathNavComponent } from 'app/course/learning-paths/components/learning-path-student-nav/learning-path-student-nav.component';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { CourseExerciseDetailsModule } from 'app/overview/exercise-details/course-exercise-details.module';
import { LearningPathLectureUnitComponent } from 'app/course/learning-paths/components/learning-path-lecture-unit/learning-path-lecture-unit.component';
import { LearningPathExerciseComponent } from 'app/course/learning-paths/components/learning-path-exercise/learning-path-exercise.component';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { LearningPathNavigationService } from 'app/course/learning-paths/services/learning-path-navigation.service';
import { EntityNotFoundError } from 'app/course/learning-paths/exceptions/entity-not-found.error';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-learning-path-student-page',
    templateUrl: './learning-path-student-page.component.html',
    styleUrl: './learning-path-student-page.component.scss',
    standalone: true,
    imports: [
        CommonModule,
        RouterModule,
        LearningPathNavComponent,
        CourseExerciseDetailsModule,
        LearningPathLectureUnitComponent,
        LearningPathExerciseComponent,
        ArtemisSharedModule,
    ],
})
export class LearningPathStudentPageComponent {
    protected readonly LearningObjectType = LearningObjectType;

    private readonly learningApiService = inject(LearningPathApiService);
    private readonly learningPathNavigationService = inject(LearningPathNavigationService);
    private readonly alertService = inject(AlertService);
    private readonly activatedRoute = inject(ActivatedRoute);

    readonly isLearningPathLoading = signal(false);
    readonly learningPath = signal<LearningPathDTO | undefined>(undefined);
    readonly courseId = toSignal(this.activatedRoute.parent!.parent!.params.pipe(map((params) => Number(params.courseId))), { requireSync: true });
    readonly currentLearningObject = this.learningPathNavigationService.currentLearningObject;
    readonly isLearningPathNavigationLoading = this.learningPathNavigationService.isLoading;

    constructor() {
        effect(() => this.loadLearningPathId(this.courseId()), { allowSignalWrites: true });
    }

    private async loadLearningPathId(courseId: number): Promise<void> {
        try {
            this.isLearningPathLoading.set(true);
            const learningPath = await this.learningApiService.getLearningPathForCurrentUser(courseId);
            this.learningPath.set(learningPath);
        } catch (error) {
            // If learning path does not exist (404) ignore the error
            if (!(error instanceof EntityNotFoundError)) {
                this.alertService.error(error);
            }
        } finally {
            this.isLearningPathLoading.set(false);
        }
    }

    async startLearningPath(): Promise<void> {
        try {
            this.isLearningPathLoading.set(true);
            if (!this.learningPath()) {
                const learningPath = await this.learningApiService.generateLearningPathForCurrentUser(this.courseId());
                this.learningPath.set(learningPath);
            }
            await this.learningApiService.startLearningPathForCurrentUser(this.learningPath()!.id);
            this.learningPath.update((learningPath) => ({ ...learningPath!, startedByStudent: true }));
        } catch (error) {
            this.alertService.error(error);
        } finally {
            this.isLearningPathLoading.set(false);
        }
    }
}
