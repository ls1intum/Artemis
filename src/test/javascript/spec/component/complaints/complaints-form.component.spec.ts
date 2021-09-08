import { async, ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ComplaintService, EntityResponseType } from 'app/complaints/complaint.service';
import { MockComplaintService } from '../../helpers/mocks/service/mock-complaint.service';

import { TranslateModule } from '@ngx-translate/core';
import { ComplaintsFormComponent } from 'app/complaints/form/complaints-form.component';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { Exercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import * as sinon from 'sinon';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';

describe('ComplaintsComponent', () => {
    const teamComplaints = 42;
    const studentComplaints = 69;
    const course: Course = { maxTeamComplaints: teamComplaints, maxComplaints: studentComplaints };
    const exercise: Exercise = { id: 1, teamMode: false } as Exercise;
    const courseExercise: Exercise = { id: 1, teamMode: false, course } as Exercise;
    const courseTeamExercise: Exercise = { id: 1, teamMode: true, course } as Exercise;
    let component: ComplaintsFormComponent;
    let fixture: ComponentFixture<ComplaintsFormComponent>;
    let complaintService: ComplaintService;
    let alertService: AlertService;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule],
            declarations: [ComplaintsFormComponent],
            providers: [
                MockProvider(AlertService),
                {
                    provide: ComplaintService,
                    useClass: MockComplaintService,
                },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ComplaintsFormComponent);
                complaintService = TestBed.inject(ComplaintService);
                alertService = TestBed.inject(AlertService);
                component = fixture.componentInstance;
                component.exercise = exercise;
            });
    }));

    it('should initialize with correct values for exam complaints', fakeAsync(() => {
        component.exercise = exercise;
        fixture.detectChanges();
        tick(100);
        expect(component.maxComplaintsPerCourse).toBe(1);
    }));

    it('should initialize with correct values for course complaints', fakeAsync(() => {
        component.exercise = courseExercise;
        fixture.detectChanges();
        tick(100);
        expect(component.maxComplaintsPerCourse).toStrictEqual(studentComplaints);
    }));

    it('should initialize with correct values for course complaints', fakeAsync(() => {
        component.exercise = courseTeamExercise;
        fixture.detectChanges();
        tick(100);
        expect(component.maxComplaintsPerCourse).toStrictEqual(teamComplaints);
    }));

    it('should submit after complaint creation', () => {
        const createStub = sinon.stub(complaintService, 'create').returns(of({} as EntityResponseType));
        const submitSpy = sinon.spy(component.submit, 'emit');
        component.createComplaint();
        expect(createStub).toHaveBeenCalledTimes(1);
        expect(submitSpy).toHaveBeenCalledTimes(1);
        expect(submitSpy).toHaveBeenCalledWith();
    });

    it('should throw unknown error after complaint creation', () => {
        const createStub = sinon.stub(complaintService, 'create').returns(throwError({ status: 500 }));
        const submitSpy = sinon.spy(component.submit, 'emit');
        const errorSpy = sinon.spy(alertService, 'error');
        component.createComplaint();
        expect(createStub).toHaveBeenCalledTimes(1);
        expect(submitSpy).not.toHaveBeenCalled();
        expect(errorSpy).toHaveBeenCalledTimes(1);
    });

    it('should throw known error after complaint creation', () => {
        const error = { error: { errorKey: 'toomanycomplaints' } } as HttpErrorResponse;
        const createStub = sinon.stub(complaintService, 'create').returns(throwError(error));
        const submitSpy = sinon.spy(component.submit, 'emit');
        const errorSpy = sinon.spy(alertService, 'error');
        const numberOfComplaints = 42;
        component.maxComplaintsPerCourse = numberOfComplaints;
        component.createComplaint();
        expect(createStub).toHaveBeenCalledTimes(1);
        expect(submitSpy).not.toHaveBeenCalled();
        expect(errorSpy).toHaveBeenCalledTimes(1);
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.complaint.tooManyComplaints', { maxComplaintNumber: numberOfComplaints });
    });
});
