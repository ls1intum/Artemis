import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { LearningObjectType, LearningPathDTO } from 'app/atlas/shared/entities/learning-path.model';
import { map } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { LearningPathNavComponent } from 'app/atlas/overview/learning-path-student-nav/learning-path-student-nav.component';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute } from '@angular/router';
import { LearningPathLectureUnitComponent } from 'app/atlas/overview/learning-path-lecture-unit/learning-path-lecture-unit.component';
import { LearningPathExerciseComponent } from 'app/atlas/overview/learning-path-exercise/learning-path-exercise.component';
import { LearningPathApiService } from 'app/atlas/shared/services/learning-path-api.service';
import { LearningPathNavigationService } from 'app/atlas/overview/learning-path-navigation.service';
import { onError } from 'app/shared/util/global.utils';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ScienceEventType } from 'app/shared/science/science.model';
import { ScienceService } from 'app/shared/science/science.service';

@Component({
    selector: 'jhi-learning-path-student-page',
    templateUrl: './learning-path-student-page.component.html',
    styleUrl: './learning-path-student-page.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [LearningPathNavComponent, LearningPathLectureUnitComponent, LearningPathExerciseComponent, TranslateDirective],
})
export class LearningPathStudentPageComponent {
    protected readonly LearningObjectType = LearningObjectType;

    private readonly learningApiService = inject(LearningPathApiService);
    private readonly learningPathNavigationService = inject(LearningPathNavigationService);
    private readonly alertService = inject(AlertService);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly scienceService = inject(ScienceService);

    readonly isLearningPathLoading = signal(false);
    readonly learningPath = signal<LearningPathDTO | undefined>(undefined);
    readonly courseId = toSignal(this.activatedRoute.parent!.params.pipe(map((params) => Number(params.courseId))), { requireSync: true });
    readonly currentLearningObject = this.learningPathNavigationService.currentLearningObject;
    readonly isLearningPathNavigationLoading = this.learningPathNavigationService.isLoading;

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            untracked(() => this.loadLearningPath(courseId));
        });
    }

    private async loadLearningPath(courseId: number): Promise<void> {
        try {
            this.isLearningPathLoading.set(true);
            const learningPath = await this.learningApiService.getLearningPathForCurrentUser(courseId);
            this.learningPath.set(learningPath);

            this.scienceService.logEvent(ScienceEventType.LEARNING_PATH__OPEN, learningPath.id);
        } catch (error) {
            // If learning path does not exist (404) ignore the error
            if (error.status != 404) {
                onError(this.alertService, error);
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
            this.learningPath.update((learningPath) => Object.assign({}, learningPath!, { startedByStudent: true }));
        } catch (error) {
            this.alertService.error(error);
        } finally {
            this.isLearningPathLoading.set(false);
        }
    }
}
