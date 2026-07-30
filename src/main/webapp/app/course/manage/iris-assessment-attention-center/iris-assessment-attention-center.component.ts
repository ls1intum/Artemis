import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { combineLatest, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { Course } from 'app/course/shared/entities/course.model';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { faBrain, faListAlt } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { RouterModule } from '@angular/router';
import { ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-iris-assessment-attention-center',
    imports: [HelpIconComponent, FaIconComponent, RouterModule, CommonModule, TranslateDirective, ArtemisTranslatePipe],
    templateUrl: './iris-assessment-attention-center.component.html',
    styleUrls: ['./iris-assessment-attention-center.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisAssessmentAttentionCenterComponent {
    protected readonly faBrain = faBrain;
    protected readonly faListAlt = faListAlt;

    course = input.required<Course>();
    assessmentEnabled = input.required<boolean>();

    private courseManagementService = inject(CourseManagementService);

    protected readonly reviewLink = computed(() => ['/course-management', this.course().id, 'iris-assessments']);
    protected readonly inClassQuizLink = computed(() => ['/course-management', this.course().id, 'iris-in-class-assessments']);

    protected readonly needsAttention = toSignal(
        combineLatest([toObservable(this.course), toObservable(this.assessmentEnabled)]).pipe(
            switchMap(([course, assessmentEnabled]) => {
                if (!assessmentEnabled || course.id === undefined) {
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

    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;
}
