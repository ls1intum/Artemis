import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faCheckCircle, faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { NgbAccordionModule } from '@ng-bootstrap/ng-bootstrap';
import { JudgementOfLearningRatingComponent } from 'app/course/competencies/judgement-of-learning-rating/judgement-of-learning-rating.component';
import { StudentMetrics } from 'app/entities/student-metrics.model';
import { getIcon } from 'app/entities/competency.model';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-course-competency-accordion',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [JudgementOfLearningRatingComponent, FontAwesomeModule, NgbAccordionModule, ArtemisSharedCommonModule],
    templateUrl: './course-competency-accordion.component.html',
    styleUrl: './course-competency-accordion.component.scss',
})
export class CourseCompetencyAccordionComponent {
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faCheckCircle = faCheckCircle;

    protected readonly getIcon = getIcon;

    readonly courseId = input.required<number>();
    readonly studentMetrics = input.required<StudentMetrics>();

    private readonly courseCompetencyMetrics = computed(() => this.studentMetrics().competencyMetrics ?? {});
    private readonly lectureUnitMetrics = computed(() => this.studentMetrics().lectureUnitStudentMetricsDTO ?? {});
    private readonly exerciseMetrics = computed(() => this.studentMetrics().exerciseMetrics ?? {});

    readonly courseCompetencies = computed(() => Object.values(this.courseCompetencyMetrics().competencyInformation ?? {}).sort((a, b) => (a.id < b.id ? -1 : 1)));
}
