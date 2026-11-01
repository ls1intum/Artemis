import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/instructor/programming-exercise-instructor-trigger-build-button.component';
import { ConfirmAutofocusModalComponent } from 'app/shared-ui/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { Subject, of, throwError } from 'rxjs';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { DialogService } from 'primeng/dynamicdialog';

describe('ProgrammingExercise Instructor Trigger Build Component', () => {
    const course = { id: 123 } as Course;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.id = 456;
    programmingExercise.title = 'Exercise 1';
    const participation = new StudentParticipation(ParticipationType.PROGRAMMING);
    participation.id = 567;

    let comp: ProgrammingExerciseInstructorTriggerBuildButtonComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructorTriggerBuildButtonComponent>;
    let submissionService: ProgrammingSubmissionService;
    let dialogService: DialogService;
    let dialogClose: Subject<any>;

    beforeEach(() => {
        dialogClose = new Subject<any>();
        TestBed.configureTestingModule({
            providers: [
                { provide: DialogService, useValue: { open: vi.fn(() => ({ onClose: dialogClose })) } },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseInstructorTriggerBuildButtonComponent);
        comp = fixture.componentInstance;
        submissionService = TestBed.inject(ProgrammingSubmissionService);
        dialogService = TestBed.inject(DialogService);

        fixture.componentRef.setInput('exercise', programmingExercise);
        fixture.componentRef.setInput('participation', participation);
        comp.lastResultIsManual.set(true);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it.each([
        [ParticipationType.PROGRAMMING, 'trigger-instructor-build'],
        [ParticipationType.TEMPLATE, 'trigger-editor-build'],
        [ParticipationType.SOLUTION, 'trigger-editor-build'],
    ])('uses the explicit command for known participation kind %s', (type, command) => {
        fixture.componentRef.setInput('participation', { id: 567, type });
        comp.lastResultIsManual.set(false);
        comp.triggerBuild(new MouseEvent('click'));
        const http = TestBed.inject(HttpTestingController);
        const req = http.expectOne({ method: 'POST', url: `api/programming/programming-exercises/456/participations/567/${command}` });
        expect(req.request.body).toEqual({});
        req.flush({});
        http.verify();
    });

    it('keeps an explicitly manual operation on the ordinary participation build route', () => {
        comp.triggerWithType(SubmissionType.MANUAL).subscribe();
        const http = TestBed.inject(HttpTestingController);
        const req = http.expectOne({ method: 'POST', url: 'api/programming/participations/567/trigger-build?submissionType=MANUAL' });
        expect(req.request.body).toEqual({});
        req.flush({});
        http.verify();
    });

    it('should trigger build', () => {
        vi.spyOn(dialogService, 'open');
        vi.spyOn(submissionService, 'triggerInstructorBuild').mockReturnValue(of());

        comp.triggerBuild({ stopPropagation: vi.fn() } as unknown as MouseEvent);

        expect(dialogService.open).toHaveBeenCalledOnce();
        expect(dialogService.open).toHaveBeenCalledWith(
            ConfirmAutofocusModalComponent,
            expect.objectContaining({
                width: '50rem',
                data: expect.objectContaining({
                    title: 'artemisApp.programmingExercise.resubmitSingle',
                }),
            }),
        );

        dialogClose.next({ confirmed: true });

        expect(submissionService.triggerInstructorBuild).toHaveBeenCalledOnce();
        expect(submissionService.triggerInstructorBuild).toHaveBeenCalledWith(456, participation.id, false);
    });
    it('does not request an instructor build without an exercise id and releases its busy state', () => {
        fixture.componentRef.setInput('exercise', new ProgrammingExercise(course, undefined));
        let completed = false;
        comp.triggerWithType(SubmissionType.INSTRUCTOR).subscribe({ complete: () => (completed = true) });
        expect(completed).toBe(true);
        expect(comp.isRetrievingBuildStatus()).toBe(false);
        TestBed.inject(HttpTestingController).expectNone((req) => req.method === 'POST');
    });

    it('releases busy state and propagates a failed instructor build', () => {
        const error = new HttpErrorResponse({ status: 403 });
        vi.spyOn(submissionService, 'triggerInstructorBuild').mockReturnValue(throwError(() => error));
        let received: unknown;
        comp.triggerWithType(SubmissionType.INSTRUCTOR).subscribe({ error: (value) => (received = value) });
        expect(received).toBe(error);
        expect(comp.isRetrievingBuildStatus()).toBe(false);
    });

    it('does not request a build without a selected participation', () => {
        fixture.componentRef.setInput('participation', undefined);
        comp.triggerWithType(SubmissionType.INSTRUCTOR).subscribe();
        comp.triggerFailed().subscribe();
        expect(comp.isRetrievingBuildStatus()).toBe(false);
        TestBed.inject(HttpTestingController).expectNone((req) => req.method === 'POST');
    });
});
