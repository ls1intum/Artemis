import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { Course } from 'app/course/shared/entities/course.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { IrisAssessmentAttentionCenterComponent } from 'app/course/manage/iris-assessment-attention-center/iris-assessment-attention-center.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

describe('IrisAssessmentAttentionCenterComponent', () => {
    let fixture: ComponentFixture<IrisAssessmentAttentionCenterComponent>;
    let component: IrisAssessmentAttentionCenterComponent;
    let getAssessmentAttentionStateStub: ReturnType<typeof vi.fn>;

    const instructorCourse = { id: 12, isAtLeastInstructor: true, isAtLeastTutor: true } as Course;

    beforeEach(async () => {
        getAssessmentAttentionStateStub = vi.fn(() => of(new HttpResponse({ body: { needsAttention: true } })));

        await TestBed.configureTestingModule({
            imports: [IrisAssessmentAttentionCenterComponent],
            providers: [provideRouter([]), { provide: CourseManagementService, useValue: { getAssessmentAttentionState: getAssessmentAttentionStateStub } }],
        })
            .overrideComponent(IrisAssessmentAttentionCenterComponent, {
                remove: {
                    imports: [HelpIconComponent, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
                },
                add: {
                    imports: [
                        MockComponent(HelpIconComponent),
                        MockComponent(FaIconComponent),
                        MockDirective(TranslateDirective),
                        MockPipe(ArtemisTranslatePipe, (key: string) => key),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(IrisAssessmentAttentionCenterComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load and render the assessment links for instructors when ask-user assessment is enabled', async () => {
        fixture.componentRef.setInput('course', instructorCourse);
        fixture.componentRef.setInput('assessmentEnabled', true);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(getAssessmentAttentionStateStub).toHaveBeenCalledExactlyOnceWith(12);
        expect((component as any).needsAttention()).toBe(true);
        expect((component as any).reviewLink()).toEqual(['/course-management', 12, 'iris-assessments']);
        expect((component as any).inClassQuizLink()).toEqual(['/course-management', 12, 'iris-in-class-assessments']);
        expect(fixture.debugElement.queryAll(By.css('a'))).toHaveLength(2);
    });

    it('should not load the attention state for non-tutors', async () => {
        fixture.componentRef.setInput('course', { ...instructorCourse, isAtLeastInstructor: false, isAtLeastTutor: false });
        fixture.componentRef.setInput('assessmentEnabled', true);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(getAssessmentAttentionStateStub).not.toHaveBeenCalled();
        expect((component as any).needsAttention()).toBe(false);
    });

    it('should not load the attention state when ask-user assessment is disabled', async () => {
        fixture.componentRef.setInput('course', instructorCourse);
        fixture.componentRef.setInput('assessmentEnabled', false);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(getAssessmentAttentionStateStub).not.toHaveBeenCalled();
        expect((component as any).needsAttention()).toBe(false);
    });

    it('should fall back to no attention needed when loading fails', async () => {
        getAssessmentAttentionStateStub.mockReturnValue(throwError(() => new Error('failed')));

        fixture.componentRef.setInput('course', instructorCourse);
        fixture.componentRef.setInput('assessmentEnabled', true);
        fixture.detectChanges();
        await fixture.whenStable();

        expect((component as any).needsAttention()).toBe(false);
    });
});
