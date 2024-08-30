import { Component, effect, inject, signal } from '@angular/core';
import { LearningObjectType } from 'app/entities/competency/learning-path.model';
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

    private readonly learningApiService: LearningPathApiService = inject(LearningPathApiService);
    private readonly learningPathNavigationService = inject(LearningPathNavigationService);
    private readonly alertService: AlertService = inject(AlertService);
    private readonly activatedRoute: ActivatedRoute = inject(ActivatedRoute);

    readonly isLearningPathIdLoading = signal(false);
    readonly learningPathId = signal<number | undefined>(undefined);
    readonly courseId = toSignal(this.activatedRoute.parent!.parent!.params.pipe(map((params) => Number(params.courseId))), { requireSync: true });
    readonly currentLearningObject = this.learningPathNavigationService.currentLearningObject;
    readonly isLearningPathNavigationLoading = this.learningPathNavigationService.isLoading;

    constructor() {
        effect(async () => await this.loadLearningPathId(this.courseId()), { allowSignalWrites: true });
    }

    private async loadLearningPathId(courseId: number): Promise<void> {
        try {
            this.isLearningPathIdLoading.set(true);
            const learningPathId = await this.learningApiService.getLearningPathId(courseId);
            this.learningPathId.set(learningPathId);
        } catch (error) {
            // If learning path does not exist (404) ignore the error
            if (!(error instanceof EntityNotFoundError)) {
                this.alertService.error(error);
            }
        } finally {
            this.isLearningPathIdLoading.set(false);
        }
    }

    async generateLearningPath(courseId: number): Promise<void> {
        try {
            this.isLearningPathIdLoading.set(true);
            const learningPathId = await this.learningApiService.generateLearningPath(courseId);
            this.learningPathId.set(learningPathId);
        } catch (error) {
            this.alertService.error(error);
        } finally {
            this.isLearningPathIdLoading.set(false);
        }
    }
}
