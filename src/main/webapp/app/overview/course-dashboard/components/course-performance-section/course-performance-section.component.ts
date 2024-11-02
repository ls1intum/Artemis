import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { CourseDashboardService } from 'app/overview/course-dashboard/course-dashboard.service';
import { StudentMetrics } from 'app/entities/student-metrics.model';
import { lastValueFrom } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { round } from 'app/shared/util/utils';
import dayjs from 'dayjs/esm';
import { ExercisePerformance } from 'app/overview/course-dashboard/course-exercise-performance/course-exercise-performance.component';
import { CourseDashboardModule } from 'app/overview/course-dashboard/course-dashboard.module';
import { faCheckCircle, faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbAccordionModule } from '@ng-bootstrap/ng-bootstrap';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { RouterModule } from '@angular/router';
import { JudgementOfLearningRatingComponent } from 'app/course/competencies/judgement-of-learning-rating/judgement-of-learning-rating.component';
import { CourseCompetencyAccordionComponent } from 'app/overview/course-dashboard/components/course-competency-accordion/course-competency-accordion.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-course-performance-section',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        CommonModule,
        CourseDashboardModule,
        FontAwesomeModule,
        NgbAccordionModule,
        RouterModule,
        JudgementOfLearningRatingComponent,
        CourseCompetencyAccordionComponent,
        TranslateDirective,
    ],
    templateUrl: './course-performance-section.component.html',
    styleUrl: './course-performance-section.component.scss',
})
export class CoursePerformanceSectionComponent {
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faCheckCircle = faCheckCircle;

    private readonly courseDashboardService = inject(CourseDashboardService);
    private readonly alertService = inject(AlertService);
    private readonly courseStorageService = inject(CourseStorageService);

    readonly courseId = input.required<number>();

    readonly isLoading = signal<boolean>(false);
    readonly studentMetrics = signal<StudentMetrics | undefined | null>(undefined);

    // TODO: This only updates when the courseId input changes but not when the course changes
    readonly course = computed(() => this.courseStorageService.getCourse(this.courseId()));
    readonly learningPathsEnabled = computed(() => !!this.course()?.learningPathsEnabled);

    readonly exerciseMetrics = computed(() => this.studentMetrics()?.exerciseMetrics ?? {});
    readonly sortedExerciseIds = computed(() =>
        Object.values(this.exerciseMetrics()?.exerciseInformation ?? {})
            .filter((exercise) => exercise.dueDate && exercise.dueDate.isBefore(dayjs()))
            .sort((a, b) => ((a.dueDate ?? a.startDate).isBefore(b.dueDate) ? -1 : 1))
            .map((exercise) => exercise.id)
            // Limit the number of exercises to the last 10
            .slice(-10),
    );

    private readonly relevantExercises = computed(() =>
        Object.values(this.exerciseMetrics().exerciseInformation ?? {}).filter((exercise) => this.sortedExerciseIds().includes(exercise.id)),
    );
    readonly points = computed(() => {
        const points = this.relevantExercises().reduce((sum, exercise) => sum + ((this.exerciseMetrics().score?.[exercise.id] || 0) / 100) * exercise.maxPoints, 0);
        return round(points, 1);
    });
    readonly maxPoints = computed(() => {
        const maxPoints = this.relevantExercises().reduce((sum, exercise) => sum + exercise.maxPoints, 0);
        return round(maxPoints, 1);
    });

    readonly hasCompetencies = computed(() => Object.keys(this.studentMetrics()?.competencyMetrics?.competencyInformation ?? {}).length > 0);

    readonly exercisePerformance = computed(() =>
        this.sortedExerciseIds().flatMap((exerciseId) => {
            const exerciseInformation = this.exerciseMetrics().exerciseInformation?.[exerciseId];
            return exerciseInformation
                ? <ExercisePerformance[]>[
                      {
                          exerciseId: exerciseId,
                          title: exerciseInformation.title,
                          shortName: exerciseInformation.shortName,
                          score: this.exerciseMetrics().score?.[exerciseId],
                          averageScore: this.exerciseMetrics().averageScore?.[exerciseId],
                      },
                  ]
                : <ExercisePerformance[]>[];
        }),
    );

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            untracked(() => this.loadStudentMetrics(courseId));
        });
    }

    private async loadStudentMetrics(courseId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            const response = await lastValueFrom(this.courseDashboardService.getCourseMetricsForUser(courseId));
            this.studentMetrics.set(response.body);
        } catch (error) {
            this.alertService.error(error);
        } finally {
            this.isLoading.set(false);
        }
    }
}
