import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { combineLatest, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { Course } from 'app/course/shared/entities/course.model';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { faBrain, faListAlt } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiButtonDirective } from 'app/shared-ui/tum-ui/button/tum-ui-button.directive';

/**
 * Displays a box on the course detail page that draws instructors'/tutors' attention to Iris assessments
 * (or in-class quiz assessments) that need review, and links to the respective review overview pages.
 */
@Component({
    selector: 'jhi-iris-assessment-attention-center',
    imports: [HelpIconComponent, FaIconComponent, RouterLink, TranslateDirective, ArtemisTranslatePipe, TumUiButtonDirective],
    templateUrl: './iris-assessment-attention-center.component.html',
    styleUrls: ['./iris-assessment-attention-center.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisAssessmentAttentionCenterComponent {
    protected readonly faBrain = faBrain;
    protected readonly faListAlt = faListAlt;

    course = input.required<Course>();
    assessmentEnabled = input.required<boolean>();

    private readonly courseManagementService = inject(CourseManagementService);

    protected readonly reviewLink = computed(() => ['/course-management', this.course().id, 'iris-assessments']);
    protected readonly inClassQuizLink = computed(() => ['/course-management', this.course().id, 'iris-in-class-assessments']);

    protected readonly needsAttention = toSignal(
        combineLatest([toObservable(this.course), toObservable(this.assessmentEnabled)]).pipe(
            switchMap(([course, assessmentEnabled]) => {
                if (!assessmentEnabled || !course.isAtLeastTutor || course.id === undefined) {
                    return of(false);
                }

                return this.courseManagementService.getAssessmentAttentionState(course.id).pipe(
                    map((response) => response.body?.needsAttention ?? false),
                    catchError(() => of(false)),
                );
            }),
        ),
        { initialValue: false },
    );
}
