import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ExternalSubmissionDialogComponent } from 'app/exercise/external-submission/external-submission-dialog.component';
import { ExternalSubmissionService } from 'app/exercise/external-submission/external-submission.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { Subject, throwError } from 'rxjs';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { User } from 'app/account/user/user.model';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { DynamicDialogRef } from 'primeng/dynamicdialog';

describe('ExternalSubmissionDialogComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExternalSubmissionDialogComponent>;
    let component: ExternalSubmissionDialogComponent;
    let externalSubmissionService: ExternalSubmissionService;
    let dialogRef: DynamicDialogRef;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;

    beforeEach(async () => {
        dialogRefCloseSpy = vi.fn();
        dialogRef = { close: dialogRefCloseSpy, onClose: new Subject<any>() } as unknown as DynamicDialogRef;

        await TestBed.configureTestingModule({
            imports: [ExternalSubmissionDialogComponent],
            providers: [
                { provide: DynamicDialogRef, useValue: dialogRef },
                MockProvider(EventManager),
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExternalSubmissionDialogComponent);
        component = fixture.componentInstance;
        externalSubmissionService = TestBed.inject(ExternalSubmissionService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should get initial result on init', () => {
        const result = new Result();
        const extServiceSpy = vi.spyOn(externalSubmissionService, 'generateInitialManualResult').mockReturnValue(result);
        component.ngOnInit();
        expect(extServiceSpy).toHaveBeenCalledOnce();
        expect(component.result).toBe(result);
    });

    it('should close the dialog on clear', () => {
        component.clear();
        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();
        expect(dialogRefCloseSpy).toHaveBeenCalledWith('cancel');
    });

    it('should save feedback correctly', () => {
        const result: Result = new Result();
        component.result = result;
        const exercise = { id: 2 } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        component.student = { id: 3 } as User;

        const subject: Subject<HttpResponse<Result>> = new Subject<HttpResponse<Result>>();
        const createMock = vi.spyOn(externalSubmissionService, 'create').mockReturnValue(subject.asObservable());

        const eventManager = TestBed.inject(EventManager);
        const eventManagerSpy = vi.spyOn(eventManager, 'broadcast').mockImplementation(() => {});

        component.feedbacks = [new Feedback(), new Feedback()];

        component.feedbacks[0].type = FeedbackType.AUTOMATIC;
        component.feedbacks[1].type = FeedbackType.AUTOMATIC_ADAPTED;

        component.save();

        expect(component.isSaving).toBe(true);
        expect(result.feedbacks).toBe(component.feedbacks);
        expect(result.feedbacks?.every((feedback) => feedback.type === FeedbackType.MANUAL)).toBe(true);
        expect(createMock).toHaveBeenCalledOnce();
        expect(createMock).toHaveBeenCalledWith(exercise, component.student, result);
        expect(dialogRefCloseSpy).not.toHaveBeenCalled();

        subject.next(new HttpResponse<Result>({ body: result }));

        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();
        expect(dialogRefCloseSpy).toHaveBeenCalledWith(result);
        expect(component.isSaving).toBe(false);
        expect(eventManagerSpy).toHaveBeenCalledOnce();
        expect(eventManagerSpy).toHaveBeenCalledWith({ name: 'resultListModification', content: 'Added a manual result' });
    });

    it('should set isSaving to false on saveError', () => {
        component.result = new Result();
        component.isSaving = true;
        fixture.componentRef.setInput('exercise', { id: 2 } as Exercise);

        const createMock = vi.spyOn(externalSubmissionService, 'create').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        const onSaveErrorSpy = vi.spyOn(component, 'onSaveError');

        component.save();
        expect(createMock).toHaveBeenCalledOnce();
        expect(onSaveErrorSpy).toHaveBeenCalledOnce();
        expect(component.isSaving).toBe(false);
    });

    it('should add a new feedback on pushFeedback and remove last on popFeedback', () => {
        expect(component.feedbacks).toHaveLength(0);
        component.pushFeedback();
        component.pushFeedback();
        expect(component.feedbacks).toHaveLength(2);
        component.popFeedback();
        expect(component.feedbacks).toHaveLength(1);
        component.popFeedback();
        expect(component.feedbacks).toHaveLength(0);
        component.popFeedback();
        expect(component.feedbacks).toHaveLength(0);
    });
});
