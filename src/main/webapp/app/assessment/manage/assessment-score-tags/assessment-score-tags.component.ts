import { Component, computed, input } from '@angular/core';
import { TumAetUiTagComponent } from '@tumaet/ui-angular';
import { Course } from 'app/course/shared/entities/course.model';
import { AssessmentScore } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { round, roundValueSpecifiedByCourseSettings } from 'app/foundation/util/utils';

/**
 * The awarded, deducted and final points of an assessment as three tags, shown in the header of the submission it grades.
 * The points are rounded like the rest of the course's scores: automatic test points, e.g. 100 points split across 13
 * tests, are fractions that would otherwise show every digit.
 */
@Component({
    selector: 'jhi-assessment-score-tags',
    templateUrl: './assessment-score-tags.component.html',
    styleUrls: ['./assessment-score-tags.component.scss'],
    imports: [TumAetUiTagComponent, ArtemisTranslatePipe],
})
export class AssessmentScoreTagsComponent {
    readonly score = input.required<AssessmentScore>();
    readonly maxPoints = input.required<number>();
    /** The course whose accuracy of scores the points are shown with; without it, they show two decimals. */
    readonly course = input<Course>();

    protected readonly roundedScore = computed(() => {
        const { awarded, deducted, total } = this.score();
        const course = this.course();
        const roundPoints = (points: number) => (course ? roundValueSpecifiedByCourseSettings(points, course) : round(points, 2));
        return { awarded: roundPoints(awarded), deducted: roundPoints(deducted), total: roundPoints(total) };
    });
}
