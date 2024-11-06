import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faCheckCircle, faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { NgbAccordionModule } from '@ng-bootstrap/ng-bootstrap';
import { JudgementOfLearningRatingComponent } from 'app/course/competencies/judgement-of-learning-rating/judgement-of-learning-rating.component';
import { StudentMetrics } from 'app/entities/student-metrics.model';
import { getIcon } from 'app/entities/competency.model';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { CourseCompetencyBodyComponent } from 'app/overview/course-dashboard/components/course-competency-body/course-competency-body.component';
import { LearningPathCompetencyDTO, LearningPathDTO } from 'app/entities/competency/learning-path.model';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-course-competency-accordion',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [JudgementOfLearningRatingComponent, FontAwesomeModule, NgbAccordionModule, ArtemisSharedCommonModule, CourseCompetencyBodyComponent],
    templateUrl: './course-competency-accordion.component.html',
    styleUrl: './course-competency-accordion.component.scss',
})
export class CourseCompetencyAccordionComponent {
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faCheckCircle = faCheckCircle;

    protected readonly getIcon = getIcon;

    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly alertService = inject(AlertService);

    readonly courseId = input.required<number>();
    readonly studentMetrics = input.required<StudentMetrics>();

    readonly isLoading = signal<boolean>(false);
    readonly courseCompetencies = signal<LearningPathCompetencyDTO[]>([]);
    readonly learningPathId = signal<number | undefined>(undefined);

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            untracked(() => this.loadCourseCompetencies(courseId));
        });
    }

    private async loadCourseCompetencies(courseId: number) {
        try {
            this.isLoading.set(true);
            await this.loadLearningPathId(courseId);
            const courseCompetencies = await this.learningPathApiService.getLearningPathCompetencies(this.learningPathId()!);
            this.courseCompetencies.set(courseCompetencies);
        } catch (error) {
            this.alertService.error(error);
        } finally {
            this.isLoading.set(false);
        }
    }

    private async loadLearningPathId(courseId: number): Promise<void> {
        let learningPathDTO: LearningPathDTO;
        try {
            learningPathDTO = await this.learningPathApiService.getLearningPathForCurrentUser(courseId);
        } catch (error) {
            if (error.status != 404) {
                throw error;
            }
            learningPathDTO = await this.learningPathApiService.generateLearningPathForCurrentUser(courseId);
        }
        this.learningPathId.set(learningPathDTO.id);
    }
}
