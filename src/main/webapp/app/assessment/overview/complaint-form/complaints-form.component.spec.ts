import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ComplaintService, EntityResponseType } from 'app/assessment/shared/services/complaint.service';
import { MockComplaintService } from 'test/helpers/mocks/service/mock-complaint.service';
import { ComplaintsFormComponent } from 'app/assessment/overview/complaint-form/complaints-form.component';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
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

    it('should initialize with correct values for exam complaints', fakeAsync(() => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        tick(100);
        expect(component.maxComplaintsPerCourse).toBe(1);
    }));

    it('should initialize with correct values for course complaints', fakeAsync(() => {
        fixture.componentRef.setInput('exercise', courseExercise);
        fixture.detectChanges();
        tick(100);
        expect(component.maxComplaintsPerCourse).toStrictEqual(studentComplaints);
    }));

    it('should initialize with correct values for course complaints for team exercises', fakeAsync(() => {
        fixture.componentRef.setInput('exercise', courseTeamExercise);
        fixture.detectChanges();
        tick(100);
        expect(component.maxComplaintsPerCourse).toStrictEqual(teamComplaints);
    }));

    it('should submit after complaint creation', () => {
        const createMock = jest.spyOn(complaintService, 'create').mockReturnValue(of({} as EntityResponseType));
        const submitSpy = jest.spyOn(component.onSubmit, 'emit');
        component.createComplaint();
        expect(createMock).toHaveBeenCalledOnce();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(submitSpy).toHaveBeenCalledWith();
    });

    it('should throw unknown error after complaint creation', () => {
        const createMock = jest.spyOn(complaintService, 'create').mockReturnValue(throwError(() => ({ status: 400 })));
        const submitSpy = jest.spyOn(component.onSubmit, 'emit');
        const errorSpy = jest.spyOn(alertService, 'error');
        component.createComplaint();
        expect(createMock).toHaveBeenCalledOnce();
        expect(submitSpy).not.toHaveBeenCalled();
        expect(errorSpy).toHaveBeenCalledOnce();
    });

    it('should throw known error after complaint creation', () => {
        const error = { error: { errorKey: 'tooManyComplaints' } } as HttpErrorResponse;
        const createMock = jest.spyOn(complaintService, 'create').mockReturnValue(throwError(() => error));
        const submitSpy = jest.spyOn(component.onSubmit, 'emit');
        const errorSpy = jest.spyOn(alertService, 'error');
        const numberOfComplaints = 42;
        component.maxComplaintsPerCourse = numberOfComplaints;
        component.createComplaint();
        expect(createMock).toHaveBeenCalledOnce();
        expect(submitSpy).not.toHaveBeenCalled();
        expect(errorSpy).toHaveBeenCalledOnce();
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.complaint.tooManyComplaints', { maxComplaintNumber: numberOfComplaints });
    });

    it('should throw exceeded complaint text error after complaint creation', () => {
        // Get course
        fixture.componentRef.setInput('exercise', courseExercise);
        component.ngOnInit();

        const submitSpy = jest.spyOn(component.onSubmit, 'emit');
        const errorSpy = jest.spyOn(alertService, 'error');
        // 26 characters
        component.complaintText = 'abcdefghijklmnopqrstuvwxyz';
        component.createComplaint();
        expect(submitSpy).not.toHaveBeenCalled();
        expect(errorSpy).toHaveBeenCalledOnce();
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.complaint.exceededComplaintTextLimit', { maxComplaintTextLimit: 20 });
    });

    it('text area should have the correct max length', fakeAsync(() => {
        // Get course
        fixture.componentRef.setInput('exercise', courseExercise);
        fixture.componentRef.setInput('isCurrentUserSubmissionAuthor', true);
        component.ngOnInit();

        fixture.detectChanges();
        tick(100);

        const responseTextArea = fixture.debugElement.query(By.css('#complainTextArea')).nativeElement;
        const complaintButton = fixture.debugElement.query(By.css('#submit-complaint')).nativeElement;
        responseTextArea.value = 'a';
        component.complaintText = 'a';

        fixture.detectChanges();
        tick(100);

        expect(responseTextArea.maxLength).toBe(20);
        expect(complaintButton.disabled).toBeFalse();
    }));

    it('submit complaint button should be disabled', fakeAsync(() => {
        // Get course
        fixture.componentRef.setInput('exercise', courseExercise);
        fixture.componentRef.setInput('isCurrentUserSubmissionAuthor', true);
        component.ngOnInit();

        fixture.detectChanges();
        tick(100);

        const responseTextArea = fixture.debugElement.query(By.css('#complainTextArea')).nativeElement;
        const complaintButton = fixture.debugElement.query(By.css('#submit-complaint')).nativeElement;

        responseTextArea.value = 'abcdefghijklmnopqrstuvwxyz';
        component.complaintText = 'abcdefghijklmnopqrstuvwxyz';

        fixture.detectChanges();
        tick(100);

        expect(complaintButton.disabled).toBeTrue();
    }));
});
