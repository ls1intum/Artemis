import { Component, OnInit, Signal, inject, signal } from '@angular/core';
import { LearningObjectType, LearningPathNavigationObjectDto } from 'app/entities/competency/learning-path.model';
import { map } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { LearningPathStudentNavComponent } from 'app/course/learning-paths/components/learning-path-student-nav/learning-path-student-nav.component';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { CourseExerciseDetailsModule } from 'app/overview/exercise-details/course-exercise-details.module';
import { LearningPathLectureUnitComponent } from 'app/course/learning-paths/components/learning-path-lecture-unit/learning-path-lecture-unit.component';
import { LearningPathExerciseComponent } from 'app/course/learning-paths/components/learning-path-exercise/learning-path-exercise.component';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { EntityNotFoundError } from 'app/course/learning-paths/exceptions/entity-not-found.error';
import { LearningPathNavigationService } from 'app/course/learning-paths/services/learning-path-navigation.service';

@Component({
    selector: 'jhi-learning-path-student-page',
    templateUrl: './learning-path-student-page.component.html',
    styleUrl: './learning-path-student-page.component.scss',
    standalone: true,
    imports: [CommonModule, RouterModule, LearningPathStudentNavComponent, CourseExerciseDetailsModule, LearningPathLectureUnitComponent, LearningPathExerciseComponent],
})
export class LearningPathStudentPageComponent implements OnInit {
    protected readonly LearningObjectType = LearningObjectType;

    private readonly learningApiService: LearningPathApiService = inject(LearningPathApiService);
    private readonly learningPathNavigationService = inject(LearningPathNavigationService);
    private readonly alertService: AlertService = inject(AlertService);
    private readonly activatedRoute: ActivatedRoute = inject(ActivatedRoute);

    readonly currentLearningObject: Signal<LearningPathNavigationObjectDto | undefined> = this.learningPathNavigationService.currentLearningObject;

    readonly isLoading = signal(false);
    readonly learningPathId = signal<number | undefined>(undefined);
    readonly courseId: Signal<number> = toSignal(this.activatedRoute.parent!.parent!.params.pipe(map((params) => params.courseId)));

    async ngOnInit(): Promise<void> {
        await this.loadLearningPathId(this.courseId());
    }

    private async loadLearningPathId(courseId: number): Promise<void> {
        this.isLoading.set(true);
        try {
            const learningPathId = await this.learningApiService.getLearningPathId(courseId);
            this.learningPathId.set(learningPathId);
        } catch (error) {
            if (error instanceof EntityNotFoundError) {
                await this.generateLearningPath(courseId);
            }
            this.alertService.error(error);
        }
        this.isLoading.set(false);
    }

    private async generateLearningPath(courseId: number): Promise<void> {
        try {
            this.learningPathId.set(await this.learningApiService.generateLearningPath(courseId));
        } catch (error) {
            this.alertService.error(error);
        }
    }
}
