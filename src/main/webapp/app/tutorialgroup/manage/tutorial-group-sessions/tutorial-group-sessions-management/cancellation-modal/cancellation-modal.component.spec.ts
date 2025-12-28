import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CancellationModalComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/tutorial-group-sessions-management/cancellation-modal/cancellation-modal.component';
import { TutorialGroupSessionService } from 'app/tutorialgroup/shared/service/tutorial-group-session.service';
import { generateExampleTutorialGroupSession } from 'test/helpers/sample/tutorialgroup/tutorialGroupSessionExampleModels';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/shared/service/alert.service';
import { MockProvider } from 'ng-mocks';

describe('CancellationModalComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CancellationModalComponent>;
    let component: CancellationModalComponent;
    const course = { id: 1, timeZone: 'Europe/Berlin' } as Course;
    const tutorialGroupId = 2;
    const tutorialGroupSessionId = 3;
    const tutorialGroupSession = generateExampleTutorialGroupSession({ id: tutorialGroupSessionId });
    let tutorialGroupSessionService: TutorialGroupSessionService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), MockProvider(AlertService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CancellationModalComponent);
                component = fixture.componentInstance;
                fixture.componentRef.setInput('tutorialGroupSession', tutorialGroupSession);
                fixture.componentRef.setInput('course', course);
                fixture.componentRef.setInput('tutorialGroupId', tutorialGroupId);

                tutorialGroupSessionService = TestBed.inject(TutorialGroupSessionService);

                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should call cancel with active session', () => {
        const cancelSessionSpy = vi.spyOn(tutorialGroupSessionService, 'cancel').mockReturnValue(of(new HttpResponse<TutorialGroupSession>({ body: tutorialGroupSession })));
        const confirmedSpy = vi.spyOn(component.confirmed, 'emit');

        // Set the reason directly
        component.reasonControl!.setValue('National Holiday');
        component.dialogVisible.set(true);

        // Call the method directly to avoid jsdom CSS parsing issues with PrimeNG dialog
        component.cancelOrActivate();

        expect(cancelSessionSpy).toHaveBeenCalledOnce();
        expect(cancelSessionSpy).toHaveBeenCalledWith(course.id, tutorialGroupId, tutorialGroupSessionId, 'National Holiday');
        expect(confirmedSpy).toHaveBeenCalledOnce();
        expect(component.dialogVisible()).toBe(false);
    });

    it('should call activate with cancelled session', () => {
        fixture.componentRef.setInput('tutorialGroupSession', { ...tutorialGroupSession, status: TutorialGroupSessionStatus.CANCELLED });
        fixture.detectChanges();

        const activateSessionSpy = vi.spyOn(tutorialGroupSessionService, 'activate').mockReturnValue(of(new HttpResponse<TutorialGroupSession>({ body: tutorialGroupSession })));
        const confirmedSpy = vi.spyOn(component.confirmed, 'emit');

        component.dialogVisible.set(true);

        // Call the method directly
        component.cancelOrActivate();

        expect(activateSessionSpy).toHaveBeenCalledOnce();
        expect(activateSessionSpy).toHaveBeenCalledWith(course.id, tutorialGroupId, tutorialGroupSessionId);
        expect(confirmedSpy).toHaveBeenCalledOnce();
        expect(component.dialogVisible()).toBe(false);
    });

    it('should open the dialog and initialize form', () => {
        expect(component.dialogVisible()).toBe(false);
        component.open();
        expect(component.dialogVisible()).toBe(true);
        expect(component.reasonControl).toBeDefined();
    });

    it('should return true for isSubmitPossible when form is valid', () => {
        component.open();
        expect(component.isSubmitPossible).toBe(true);
    });

    it('should return false for isSubmitPossible when form is invalid', () => {
        component.open();
        // Set reason to a string longer than 255 characters to make form invalid
        component.reasonControl!.setValue('a'.repeat(256));
        expect(component.isSubmitPossible).toBe(false);
    });

    it('should return empty string for generateSessionLabel when session has no start or end', () => {
        const sessionWithoutDates = { ...tutorialGroupSession, start: undefined, end: undefined } as TutorialGroupSession;
        expect(component.generateSessionLabel(sessionWithoutDates)).toBe('');
    });

    it('should handle error when canceling session fails', () => {
        const errorResponse = new HttpErrorResponse({ status: 400 });
        vi.spyOn(tutorialGroupSessionService, 'cancel').mockReturnValue(throwError(() => errorResponse));
        const alertService = TestBed.inject(AlertService);
        const alertErrorSpy = vi.spyOn(alertService, 'error');

        component.reasonControl!.setValue('National Holiday');
        component.dialogVisible.set(true);
        component.cancelOrActivate();

        expect(alertErrorSpy).toHaveBeenCalledWith('error.http.400');
        expect(component.dialogVisible()).toBe(false);
    });

    it('should handle error when activating session fails', () => {
        fixture.componentRef.setInput('tutorialGroupSession', { ...tutorialGroupSession, status: TutorialGroupSessionStatus.CANCELLED });
        fixture.detectChanges();

        const errorResponse = new HttpErrorResponse({ status: 400 });
        vi.spyOn(tutorialGroupSessionService, 'activate').mockReturnValue(throwError(() => errorResponse));
        const alertService = TestBed.inject(AlertService);
        const alertErrorSpy = vi.spyOn(alertService, 'error');

        component.dialogVisible.set(true);
        component.cancelOrActivate();

        expect(alertErrorSpy).toHaveBeenCalledWith('error.http.400');
        expect(component.dialogVisible()).toBe(false);
    });
});
