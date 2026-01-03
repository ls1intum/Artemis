import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { EditTutorialGroupSessionComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/edit-tutorial-group-session/edit-tutorial-group-session.component';
import { TutorialGroupSessionService } from 'app/tutorialgroup/shared/service/tutorial-group-session.service';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import {
    formDataToTutorialGroupSessionDTO,
    generateExampleTutorialGroupSession,
    tutorialGroupSessionToTutorialGroupSessionFormData,
} from 'test/helpers/sample/tutorialgroup/tutorialGroupSessionExampleModels';
import { Course } from 'app/core/course/shared/entities/course.model';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupSessionFormComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import '@angular/localize/init';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import * as Sentry from '@sentry/angular';
describe('EditTutorialGroupSessionComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<EditTutorialGroupSessionComponent>;
    let component: EditTutorialGroupSessionComponent;
    let sessionService: TutorialGroupSessionService;
    let exampleSession: TutorialGroupSession;
    let exampleTutorialGroup: TutorialGroup;

    const timeZone = 'Europe/Berlin';
    const course = {
        id: 1,
        timeZone,
    } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [EditTutorialGroupSessionComponent, OwlNativeDateTimeModule],
            providers: [MockProvider(TutorialGroupSessionService), MockProvider(AlertService), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(EditTutorialGroupSessionComponent);
        component = fixture.componentInstance;
        sessionService = TestBed.inject(TutorialGroupSessionService);
        exampleSession = generateExampleTutorialGroupSession({});
        exampleTutorialGroup = generateExampleTutorialGroup({});
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('tutorialGroupSession', exampleSession);
        fixture.componentRef.setInput('tutorialGroup', exampleTutorialGroup);
        component.open();
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should set form data correctly', () => {
        const formStub: TutorialGroupSessionFormComponent = fixture.debugElement.query(By.directive(TutorialGroupSessionFormComponent)).componentInstance;
        fixture.detectChanges();
        expect(component.formData).toEqual(tutorialGroupSessionToTutorialGroupSessionFormData(exampleSession, timeZone));
        expect(formStub.formData()).toEqual(component.formData);
    });

    it('should send PUT request upon form submission and close dialog', () => {
        const changedSession: TutorialGroupSession = {
            ...exampleSession,
            location: 'Changed',
        };

        const updateResponse: HttpResponse<TutorialGroupSession> = new HttpResponse({
            body: changedSession,
            status: 200,
        });

        const updatedStub = vi.spyOn(sessionService, 'update').mockReturnValue(of(updateResponse));
        const sessionUpdatedSpy = vi.spyOn(component.sessionUpdated, 'emit');

        const sessionForm: TutorialGroupSessionFormComponent = fixture.debugElement.query(By.directive(TutorialGroupSessionFormComponent)).componentInstance;

        const formData = tutorialGroupSessionToTutorialGroupSessionFormData(changedSession, timeZone);

        sessionForm.formSubmitted.emit(formData);

        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(course.id!, exampleTutorialGroup.id!, exampleSession.id!, formDataToTutorialGroupSessionDTO(formData));
        expect(sessionUpdatedSpy).toHaveBeenCalledOnce();
        expect(component.dialogVisible()).toBe(false);
    });

    it('should call onError and close dialog when update fails', () => {
        const errorResponse = new HttpErrorResponse({
            error: { errorKey: 'sessionOverlapsWithSession', message: 'Session overlaps', params: {} },
            status: 400,
        });

        vi.spyOn(sessionService, 'update').mockReturnValue(throwError(() => errorResponse));
        const alertService = TestBed.inject(AlertService);
        const alertErrorSpy = vi.spyOn(alertService, 'error');

        const sessionForm: TutorialGroupSessionFormComponent = fixture.debugElement.query(By.directive(TutorialGroupSessionFormComponent)).componentInstance;
        const formData = tutorialGroupSessionToTutorialGroupSessionFormData(exampleSession, timeZone);

        sessionForm.formSubmitted.emit(formData);

        expect(alertErrorSpy).toHaveBeenCalledWith('Session overlaps', {});
        expect(component.dialogVisible()).toBe(false);
    });

    it('should call onError with unexpected error when error key is not sessionOverlapsWithSession', () => {
        const errorResponse = new HttpErrorResponse({
            error: { errorKey: 'otherError' },
            status: 500,
            statusText: 'Internal Server Error',
        });

        vi.spyOn(sessionService, 'update').mockReturnValue(throwError(() => errorResponse));
        const alertService = TestBed.inject(AlertService);
        const alertErrorSpy = vi.spyOn(alertService, 'error');

        const sessionForm: TutorialGroupSessionFormComponent = fixture.debugElement.query(By.directive(TutorialGroupSessionFormComponent)).componentInstance;
        const formData = tutorialGroupSessionToTutorialGroupSessionFormData(exampleSession, timeZone);

        sessionForm.formSubmitted.emit(formData);

        expect(alertErrorSpy).toHaveBeenCalledWith('error.unexpectedError', expect.objectContaining({ error: expect.any(String) }));
        expect(component.dialogVisible()).toBe(false);
    });

    it('should return early from updateSession when IDs are missing', () => {
        const captureExceptionSpy = vi.spyOn(Sentry, 'captureException');
        const sessionWithoutId = generateExampleTutorialGroupSession({});
        sessionWithoutId.id = undefined;

        fixture.componentRef.setInput('tutorialGroupSession', sessionWithoutId);
        fixture.detectChanges();

        const sessionForm: TutorialGroupSessionFormComponent = fixture.debugElement.query(By.directive(TutorialGroupSessionFormComponent)).componentInstance;
        const formData = tutorialGroupSessionToTutorialGroupSessionFormData(exampleSession, timeZone);

        sessionForm.formSubmitted.emit(formData);

        expect(captureExceptionSpy).toHaveBeenCalledWith('Error: Course, TutorialGroup, or TutorialGroupSession ID is missing');
    });
});
