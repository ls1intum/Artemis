import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { GradingComponent } from './grading.component';
import { GradingPresentationsComponent } from './grading-presentations/grading-presentations.component';
import { GradingInfoModalComponent } from './grading-info-modal/grading-info-modal.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Course } from 'app/course/shared/entities/course.model';
import { HttpResponse } from '@angular/common/http';

describe('GradingComponent grading scale requests', () => {
    let http: HttpTestingController;

    const gradeStepsResponse = [
        { id: 11, gradeName: 'Fail', lowerBoundPercentage: 0, lowerBoundInclusive: true, upperBoundPercentage: 50, upperBoundInclusive: true, isPassingGrade: false },
        { id: 12, gradeName: 'Pass', lowerBoundPercentage: 50, lowerBoundInclusive: false, upperBoundPercentage: 80, upperBoundInclusive: true, isPassingGrade: true },
        { id: 13, gradeName: 'Excellent', lowerBoundPercentage: 80, lowerBoundInclusive: false, upperBoundPercentage: 100, upperBoundInclusive: true, isPassingGrade: true },
    ];
    const gradingScaleResponse = {
        id: 5,
        gradeSteps: { title: 'Course', gradeType: 'GRADE', gradeSteps: gradeStepsResponse, maxPoints: 100, plagiarismGrade: 'U', noParticipationGrade: 'X' },
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [GradingComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ActivatedRoute, useValue: { params: of({ courseId: 123 }) } },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(CourseManagementService, { findForInstructor: () => of(new HttpResponse({ body: { id: 123, maxPoints: 100 } as Course })) }),
                MockProvider(ExamManagementService),
            ],
        })
            .overrideComponent(GradingComponent, {
                remove: {
                    imports: [DocumentationButtonComponent, GradingInfoModalComponent, HelpIconComponent, TranslateDirective, DeleteButtonDirective, GradingPresentationsComponent],
                },
                add: {
                    imports: [
                        MockComponent(DocumentationButtonComponent),
                        MockComponent(GradingInfoModalComponent),
                        MockComponent(HelpIconComponent),
                        MockDirective(TranslateDirective),
                        MockDirective(DeleteButtonDirective),
                        MockComponent(GradingPresentationsComponent),
                    ],
                },
            })
            .compileComponents();
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        http.verify();
        vi.restoreAllMocks();
    });

    it('keeps the ids of the loaded grade steps, which the editor needs to find the last step', () => {
        const fixture = TestBed.createComponent(GradingComponent);
        fixture.detectChanges();
        http.expectOne({ method: 'GET', url: '/api/assessment/courses/123/grading-scale' }).flush(gradingScaleResponse);

        const component = fixture.componentInstance;
        expect(component.existingGradingScale()).toBe(true);
        expect(component.gradingScale.gradeSteps.map((step) => step.id)).toEqual([11, 12, 13]);
        // Without the ids every step counts as the last one and the exclusive lower bound of "Pass" would be ignored.
        expect(component.lowerBoundInclusivity).toBe(false);
    });

    it('sends the grade steps without ids when the scale is saved', () => {
        const fixture = TestBed.createComponent(GradingComponent);
        fixture.detectChanges();
        http.expectOne({ method: 'GET', url: '/api/assessment/courses/123/grading-scale' }).flush(gradingScaleResponse);

        fixture.componentInstance.save();

        const request = http.expectOne({ method: 'PUT', url: '/api/assessment/courses/123/grading-scale' });
        expect(request.request.body.gradeSteps).toHaveLength(3);
        expect(request.request.body.gradeSteps.every((step: object) => !('id' in step) || (step as { id?: number }).id === undefined)).toBe(true);
        request.flush(gradingScaleResponse);
        expect(fixture.componentInstance.existingGradingScale()).toBe(true);
    });
});
