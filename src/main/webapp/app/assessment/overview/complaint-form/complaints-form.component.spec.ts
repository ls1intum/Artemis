import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ComplaintService, EntityResponseType } from 'app/assessment/shared/services/complaint.service';
import { ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { MockComplaintService } from 'test/helpers/mocks/service/mock-complaint.service';
import { ComplaintsFormComponent } from 'app/assessment/overview/complaint-form/complaints-form.component';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/foundation/service/alert.service';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';

describe('ComplaintsFormComponent', () => {
    const teamComplaints = 42;
    const studentComplaints = 69;
    const course: Course = { maxTeamComplaints: teamComplaints, maxComplaints: studentComplaints, maxComplaintTextLimit: 20 };
    const exercise: Exercise = { id: 1, teamMode: false } as Exercise;
    const courseExercise: Exercise = { id: 1, teamMode: false, course } as Exercise;
    const courseTeamExercise: Exercise = { id: 1, teamMode: true, course } as Exercise;
    let component: ComplaintsFormComponent;
    let fixture: ComponentFixture<ComplaintsFormComponent>;
    let complaintService: ComplaintService;
    let alertService: AlertService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                {
                    provide: ComplaintService,
                    useClass: MockComplaintService,
                },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(AlertService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ComplaintsFormComponent);
                complaintService = TestBed.inject(ComplaintService);
                alertService = TestBed.inject(AlertService);
                component = fixture.componentInstance;
                fixture.componentRef.setInput('exercise', exercise);
                fixture.componentRef.setInput('resultId', undefined);
                fixture.componentRef.setInput('complaintType', undefined);
            });
    });

    it('should initialize with correct values for exam complaints', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.changeDetectorRef.detectChanges();

        expect(component.maxComplaintsPerCourse).toBe(1);
    });

    it('should initialize with correct values for course complaints', () => {
        fixture.componentRef.setInput('exercise', courseExercise);
        fixture.changeDetectorRef.detectChanges();

        expect(component.maxComplaintsPerCourse).toStrictEqual(studentComplaints);
    });

    it('should initialize with correct values for course complaints for team exercises', () => {
        fixture.componentRef.setInput('exercise', courseTeamExercise);
        fixture.changeDetectorRef.detectChanges();

        expect(component.maxComplaintsPerCourse).toStrictEqual(teamComplaints);
    });

    it('should submit after complaint creation', () => {
        const createMock = vi.spyOn(complaintService, 'create').mockReturnValue(of({} as EntityResponseType));
        const submitSpy = vi.spyOn(component.onSubmit, 'emit');
        component.createComplaint();
        expect(createMock).toHaveBeenCalledTimes(1);
        expect(submitSpy).toHaveBeenCalledTimes(1);
        expect(submitSpy).toHaveBeenCalledWith();
    });

    it('should throw unknown error after complaint creation', () => {
        const createMock = vi.spyOn(complaintService, 'create').mockReturnValue(throwError(() => ({ status: 400 })));
        const submitSpy = vi.spyOn(component.onSubmit, 'emit');
        const errorSpy = vi.spyOn(alertService, 'error');
        component.createComplaint();
        expect(createMock).toHaveBeenCalledTimes(1);
        expect(submitSpy).not.toHaveBeenCalled();
        expect(errorSpy).toHaveBeenCalledTimes(1);
    });

    it('should throw known error after complaint creation', () => {
        const error = { error: { errorKey: 'tooManyComplaints' } } as HttpErrorResponse;
        const createMock = vi.spyOn(complaintService, 'create').mockReturnValue(throwError(() => error));
        const submitSpy = vi.spyOn(component.onSubmit, 'emit');
        const errorSpy = vi.spyOn(alertService, 'error');
        const numberOfComplaints = 42;
        component.maxComplaintsPerCourse = numberOfComplaints;
        component.createComplaint();
        expect(createMock).toHaveBeenCalledTimes(1);
        expect(submitSpy).not.toHaveBeenCalled();
        expect(errorSpy).toHaveBeenCalledTimes(1);
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.complaint.tooManyComplaints', { maxComplaintNumber: numberOfComplaints });
    });

    it('should throw exceeded complaint text error after complaint creation', () => {
        // Get course
        fixture.componentRef.setInput('exercise', courseExercise);
        component.ngOnInit();

        const submitSpy = vi.spyOn(component.onSubmit, 'emit');
        const errorSpy = vi.spyOn(alertService, 'error');
        // 26 characters
        component.complaintText = 'abcdefghijklmnopqrstuvwxyz';
        component.createComplaint();
        expect(submitSpy).not.toHaveBeenCalled();
        expect(errorSpy).toHaveBeenCalledTimes(1);
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.complaint.exceededComplaintTextLimit', { maxComplaintTextLimit: 20 });
    });

    it('text area should have the correct max length', () => {
        // Get course
        fixture.componentRef.setInput('exercise', courseExercise);
        fixture.componentRef.setInput('isCurrentUserSubmissionAuthor', true);
        component.ngOnInit();

        fixture.changeDetectorRef.detectChanges();

        const responseTextArea = fixture.debugElement.query(By.css('#complainTextArea')).nativeElement;
        const complaintButton = fixture.debugElement.query(By.css('#submit-complaint')).nativeElement;
        // Drive the value through the [(ngModel)] input event so the (zoneless) change detection
        // re-evaluates the button's [disabled] binding; directly mutating the plain `complaintText`
        // field no longer marks the binding dirty in Angular's zoneless reactivity model.
        responseTextArea.value = 'a';
        responseTextArea.dispatchEvent(new Event('input'));

        fixture.changeDetectorRef.detectChanges();

        expect(responseTextArea.maxLength).toBe(20);
        expect(complaintButton.disabled).toBe(false);
    });

    it('submit complaint button should be disabled', () => {
        // Get course
        fixture.componentRef.setInput('exercise', courseExercise);
        fixture.componentRef.setInput('isCurrentUserSubmissionAuthor', true);
        component.ngOnInit();

        fixture.changeDetectorRef.detectChanges();

        const responseTextArea = fixture.debugElement.query(By.css('#complainTextArea')).nativeElement;
        const complaintButton = fixture.debugElement.query(By.css('#submit-complaint')).nativeElement;

        // 26 characters, exceeding the course limit of 20 -> button must be disabled.
        // Drive the value through the [(ngModel)] input event so the disabled binding re-evaluates
        // under zoneless change detection (direct field mutation no longer marks the binding dirty).
        responseTextArea.value = 'abcdefghijklmnopqrstuvwxyz';
        responseTextArea.dispatchEvent(new Event('input'));

        fixture.changeDetectorRef.detectChanges();

        expect(complaintButton.disabled).toBe(true);
    });

    describe('length of the entered text', () => {
        beforeEach(() => {
            fixture.componentRef.setInput('exercise', courseExercise);
            fixture.componentRef.setInput('isCurrentUserSubmissionAuthor', true);
            component.ngOnInit();
            fixture.changeDetectorRef.detectChanges();
        });

        it('should be the length of the text of this form', () => {
            component.complaintText = 'abc';

            expect(component.complaintTextLength()).toBe(3);
        });

        it('should be 0 before anything was entered', () => {
            expect(component.complaintTextLength()).toBe(0);
        });

        it('should not read the text area of another complaint area on the same page', () => {
            // The exam summary shows several complaint areas, so the id of the text area is not unique. A read-only one of an exercise above is first in the document.
            const other = document.createElement('textarea');
            other.id = 'complainTextArea';
            other.value = 'a complaint of another exercise that is longer than the limit of this form';
            document.body.prepend(other);
            try {
                const textArea = fixture.debugElement.query(By.css('#complainTextArea')).nativeElement as HTMLTextAreaElement;
                const submit = fixture.debugElement.query(By.css('#submit-complaint')).nativeElement as HTMLButtonElement;
                textArea.value = 'short';
                textArea.dispatchEvent(new Event('input'));
                fixture.changeDetectorRef.detectChanges();

                expect(component.complaintTextLength()).toBe(5);
                expect(submit.disabled).toBe(false);
            } finally {
                other.remove();
            }
        });
    });

    describe('layout', () => {
        beforeEach(() => {
            fixture.componentRef.setInput('exercise', courseExercise);
            fixture.componentRef.setInput('isCurrentUserSubmissionAuthor', true);
            fixture.componentRef.setInput('complaintType', ComplaintType.COMPLAINT);
            fixture.changeDetectorRef.detectChanges();
        });

        it('should render the heading as a 16px semibold section heading', () => {
            const heading = fixture.debugElement.query(By.css('h3')).nativeElement as HTMLElement;

            // Bootstrap's unlayered heading rules win over layered utilities, so the important modifier is required.
            expect(heading.classList.contains('text-base!')).toBe(true);
            expect(heading.classList.contains('font-semibold!')).toBe(true);
        });

        it('should render the heading for a request for more feedback in the same style', () => {
            fixture.componentRef.setInput('complaintType', ComplaintType.MORE_FEEDBACK);
            fixture.changeDetectorRef.detectChanges();

            const heading = fixture.debugElement.query(By.css('h3')).nativeElement as HTMLElement;
            expect(heading.classList.contains('text-base!')).toBe(true);
            expect(heading.classList.contains('font-semibold!')).toBe(true);
        });

        it('should present the form in a card of the same language as the complaint and the feedback cards', () => {
            const card = fixture.nativeElement.querySelector('[data-testid="complaint-form-card"]') as HTMLElement;

            expect(card).toBeTruthy();
            for (const utility of ['mt-6!', 'rounded-lg', 'border', 'border-(--border-color)', 'bg-(--module-bg)', 'p-4!']) {
                expect(card.classList.contains(utility), utility).toBe(true);
            }
            // The card holds everything of the form, and the width is left to the host.
            expect(card.contains(fixture.nativeElement.querySelector('h3'))).toBe(true);
            expect(card.contains(fixture.nativeElement.querySelector('#complainTextArea'))).toBe(true);
            expect(card.contains(fixture.nativeElement.querySelector('#submit-complaint'))).toBe(true);
            expect(fixture.nativeElement.classList.contains('block')).toBe(true);
        });

        it('should not use the Bootstrap grid any more', () => {
            expect(fixture.nativeElement.querySelector('.row, [class*="col-"]')).toBeNull();
        });

        it('should render the submit button as a small TUM AET UI button', () => {
            const button = fixture.debugElement.query(By.css('#submit-complaint')).nativeElement as HTMLButtonElement;

            expect(button.classList.contains('btn')).toBe(false);
            expect(button.classList.contains('tumaet-ui-btn')).toBe(true);
            // The small size (text-sm, py-1.5) makes the button 34px high, like the page-level buttons.
            expect(button.classList.contains('tumaet:text-sm')).toBe(true);
            expect(button.classList.contains('tumaet:py-1.5')).toBe(true);
            expect(button.classList.contains('tumaet:py-2')).toBe(false);
            expect(button.disabled).toBe(true);
        });
    });
});
