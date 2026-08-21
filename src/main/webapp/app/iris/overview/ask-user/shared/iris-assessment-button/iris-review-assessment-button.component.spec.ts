import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockComponent, MockDirective, MockPipe, ngMocks } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { IrisReviewAssessmentButtonComponent } from 'app/iris/overview/ask-user/shared/iris-assessment-button/iris-review-assessment-button.component';
import { IrisVerdict, IrisVerdictReview } from 'app/iris/shared/entities/iris-verdict.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

describe('IrisReviewAssessmentButtonComponent', () => {
    let fixture: ComponentFixture<IrisReviewAssessmentButtonComponent>;
    let component: IrisReviewAssessmentButtonComponent;

    const course = { id: 7 } as Course;
    const exercise = { id: 12 } as ProgrammingExercise;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [IrisReviewAssessmentButtonComponent],
            providers: [provideRouter([])],
        })
            .overrideComponent(IrisReviewAssessmentButtonComponent, {
                remove: { imports: [FeatureToggleDirective, ArtemisTranslatePipe, FaIconComponent] },
                add: { imports: [MockDirective(FeatureToggleDirective), MockPipe(ArtemisTranslatePipe, (key: string) => key), MockComponent(FaIconComponent)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(IrisReviewAssessmentButtonComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('smallButton', true);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should mark a suspicious unreviewed assessment as needing attention', () => {
        fixture.componentRef.setInput('participation', {
            id: 1,
            irisAssessment: { id: 21, verdict: IrisVerdict.SUSPICIOUS },
        } as ProgrammingExerciseStudentParticipation);
        fixture.detectChanges();

        expect((component as any).buttonSeverity()).toBe('danger');
        expect((component as any).label()).toBe('artemisApp.exerciseActions.reviewIrisAssessment.suspicious');
        expect((component as any).assessmentReviewPath()).toBe('iris-assessments');
    });

    it('should show accepted assessments as successful and use the in-class review path', () => {
        fixture.componentRef.setInput('participation', {
            id: 1,
            irisAssessment: { id: 22, verdict: IrisVerdict.SUSPICIOUS, verdictReview: IrisVerdictReview.ACCEPTED },
        } as ProgrammingExerciseStudentParticipation);
        fixture.componentRef.setInput('inClass', true);
        fixture.detectChanges();

        expect((component as any).buttonSeverity()).toBe('success');
        expect((component as any).label()).toBe('artemisApp.exerciseActions.reviewIrisAssessment.accepted');
        expect((component as any).assessmentReviewPath()).toBe('iris-in-class-assessments');
    });

    it('should mark rejected assessments as needing attention', () => {
        fixture.componentRef.setInput('participation', {
            id: 1,
            irisAssessment: { id: 24, verdict: IrisVerdict.UNSUSPICIOUS, verdictReview: IrisVerdictReview.REJECTED },
        } as ProgrammingExerciseStudentParticipation);
        fixture.detectChanges();

        expect((component as any).buttonSeverity()).toBe('danger');
        expect((component as any).label()).toBe('artemisApp.exerciseActions.reviewIrisAssessment.rejected');
    });

    it('should show unreviewed unsuspicious assessments as successful', () => {
        fixture.componentRef.setInput('participation', {
            id: 1,
            irisAssessment: { id: 25, verdict: IrisVerdict.UNSUSPICIOUS },
        } as ProgrammingExerciseStudentParticipation);
        fixture.detectChanges();

        expect((component as any).buttonSeverity()).toBe('success');
        expect((component as any).label()).toBe('artemisApp.exerciseActions.reviewIrisAssessment.unsuspicious');
    });

    it('should disable the button when the assessment verdict is missing', () => {
        fixture.componentRef.setInput('participation', {
            id: 1,
            irisAssessment: { id: 23 },
        } as ProgrammingExerciseStudentParticipation);
        fixture.detectChanges();

        const featureToggle = fixture.debugElement.query(By.directive(FeatureToggleDirective));

        expect(ngMocks.input(featureToggle, 'overwriteDisabled')).toBe(true);
        expect((component as any).label()).toBe('artemisApp.exerciseActions.reviewIrisAssessment.missing');
    });
});
