import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { Subject, of } from 'rxjs';
import { GradingComponent } from './grading.component';
import { GradingService } from './grading-service';
import { GradingPresentationsComponent, PresentationType } from './grading-presentations/grading-presentations.component';
import { GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { toGradingScaleDTO } from 'app/assessment/shared/entities/grading-scale-dto.model';
import { Course } from 'app/course/shared/entities/course.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { GradingInfoModalComponent } from './grading-info-modal/grading-info-modal.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { deepClone } from 'app/foundation/util/deep-clone.util';

describe('Grading presentation controls with the course grading editor', () => {
    beforeEach(() => {
        vi.useFakeTimers();
        TestBed.configureTestingModule({
            imports: [GradingComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ActivatedRoute, useValue: { params: of({ courseId: 123 }) } },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(CourseManagementService),
                MockProvider(ExamManagementService),
            ],
        }).overrideComponent(GradingComponent, {
            remove: { imports: [DocumentationButtonComponent, GradingInfoModalComponent, HelpIconComponent, TranslateDirective, DeleteButtonDirective] },
            add: {
                imports: [
                    MockComponent(DocumentationButtonComponent),
                    MockComponent(GradingInfoModalComponent),
                    MockComponent(HelpIconComponent),
                    MockDirective(TranslateDirective),
                    MockDirective(DeleteButtonDirective),
                ],
            },
        });
        // Keep both the presentation child and its mode picker real; mocking either hides parent/child feedback loops.
        TestBed.overrideComponent(GradingPresentationsComponent, {
            remove: { imports: [HelpIconComponent, TranslateDirective] },
            add: { imports: [MockComponent(HelpIconComponent), MockDirective(TranslateDirective)] },
        });
    });

    afterEach(() => {
        TestBed.resetTestingModule();
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    async function setup(scale = new GradingScale(), presentationScore?: number) {
        const courseResponse = new Subject<HttpResponse<Course>>();
        vi.spyOn(TestBed.inject(CourseManagementService), 'findForInstructor').mockReturnValue(courseResponse);
        const service = TestBed.inject(GradingService);
        scale.id = 1;
        scale.gradeSteps = [
            { gradeName: 'Fail', lowerBoundPercentage: 0, upperBoundPercentage: 50, lowerBoundInclusive: true, upperBoundInclusive: false, isPassingGrade: false },
            { gradeName: 'Pass', lowerBoundPercentage: 50, upperBoundPercentage: 100, lowerBoundInclusive: true, upperBoundInclusive: true, isPassingGrade: true },
        ];
        vi.spyOn(service, 'findGradingScaleForCourse').mockReturnValue(of(new HttpResponse({ body: toGradingScaleDTO(scale) })));
        const save = vi.spyOn(service, 'updateGradingScaleForCourse').mockImplementation((_courseId, saved) => of(new HttpResponse({ body: toGradingScaleDTO(saved) })));
        const fixture = TestBed.createComponent(GradingComponent);
        const component = fixture.componentInstance;
        fixture.detectChanges();
        // Course data arrives after the scale, as it does through the two real HTTP requests.
        courseResponse.next(new HttpResponse({ body: { id: 123, maxPoints: 100, presentationScore } as Course }));
        const settle = async () => {
            for (let i = 0; i < 4; i++) {
                fixture.detectChanges();
                await vi.runOnlyPendingTimersAsync();
            }
            fixture.detectChanges();
        };
        await settle();
        const child = fixture.debugElement.query(By.directive(GradingPresentationsComponent)).componentInstance as GradingPresentationsComponent;
        const modeButton = (mode: string) =>
            Array.from(fixture.nativeElement.querySelectorAll('jhi-grading-presentations button') as NodeListOf<HTMLButtonElement>).find((button) =>
                button.textContent?.includes(`presentationType.${mode}`),
            )!;
        const enter = async (id: string, value: string) => {
            const input = fixture.nativeElement.querySelector(`#${id}`) as HTMLInputElement;
            expect(input).not.toBeNull();
            input.value = value;
            input.dispatchEvent(new Event('input'));
            input.dispatchEvent(new Event('blur'));
            await settle();
        };
        return { fixture, component, child, save, settle, modeButton, enter };
    }

    it('preserves Basic presentation requirements when saving an asynchronously loaded course', async () => {
        const { fixture, component, modeButton, save, settle } = await setup(undefined, 3);
        expect(modeButton('basic').getAttribute('aria-pressed')).toBe('true');
        expect(component.presentationsConfig().presentationScore).toBe(3);
        component.save();
        await settle();
        const saved = deepClone(save.mock.calls[0][1]);
        expect(saved.course?.presentationScore).toBe(3);
        fixture.destroy();
        const restored = await setup(saved, saved.course?.presentationScore);
        expect(restored.modeButton('basic').getAttribute('aria-pressed')).toBe('true');
        expect(restored.component.presentationsConfig().presentationScore).toBe(3);
    });

    it('keeps Graded selected, persists edits and restores them from the saved scale', async () => {
        const { fixture, component, modeButton, save, settle, enter } = await setup(undefined, 3);
        modeButton('graded').click();
        await settle();
        expect(modeButton('graded').getAttribute('aria-pressed')).toBe('true');
        expect(component.gradingScale.presentationsNumber).toBe(2);
        expect(component.gradingScale.presentationsWeight).toBe(20);
        component.gradeStepsModel.update((model) => ({ ...model, plagiarismGrade: 'PL' }));
        await settle();
        expect(modeButton('graded').getAttribute('aria-pressed')).toBe('true');
        await enter('field_numberPresentations', '5');
        await enter('field_weightPresentations', '35');
        component.save();
        await settle();
        const saved = deepClone(save.mock.calls[0][1]);
        expect(saved.presentationsNumber).toBe(5);
        expect(saved.presentationsWeight).toBe(35);
        expect(saved.course?.presentationScore).toBeUndefined();
        fixture.destroy();
        const restored = await setup(saved);
        expect(restored.modeButton('graded').getAttribute('aria-pressed')).toBe('true');
        expect(restored.fixture.nativeElement.querySelector('#field_numberPresentations').value).toBe('5');
        expect(restored.fixture.nativeElement.querySelector('#field_weightPresentations').value).toBe('35');
    });

    it('keeps incomplete Graded inputs visible through grade-step edits and accepts zero weight', async () => {
        const { component, modeButton, settle, enter } = await setup();
        modeButton('graded').click();
        await settle();
        await enter('field_numberPresentations', '');
        component.gradeStepsModel.update((model) => ({ ...model, plagiarismGrade: 'PL' }));
        await settle();
        expect(modeButton('graded').getAttribute('aria-pressed')).toBe('true');
        expect(component.validPresentationsConfig()).toBe(false);
        await enter('field_numberPresentations', '5');
        await enter('field_weightPresentations', '');
        expect(modeButton('graded').getAttribute('aria-pressed')).toBe('true');
        expect(component.validPresentationsConfig()).toBe(false);
        await enter('field_weightPresentations', '0');
        expect(component.validPresentationsConfig()).toBe(true);
        expect(component.gradingScale.presentationsWeight).toBe(0);
    });

    it('stops emitting after initialization and parent echoes settle', async () => {
        const { child, component, settle } = await setup(undefined, 3);
        const emit = vi.spyOn(child.presentationsConfigChange, 'emit');
        await settle();
        expect(emit).not.toHaveBeenCalled();
        const equivalentScale = component.gradingScale;
        component.gradingScale = equivalentScale;
        await settle();
        expect(emit).not.toHaveBeenCalled();
        expect(component.presentationsConfig().presentationType).toBe(PresentationType.BASIC);
    });
});
