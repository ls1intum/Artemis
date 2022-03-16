import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { ExternalSubmissionDialogComponent } from 'app/exercises/shared/external-submission/external-submission-dialog.component';
import { ExternalSubmissionService } from 'app/exercises/shared/external-submission/external-submission.service';
import { Result } from 'app/entities/result.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject, throwError } from 'rxjs';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { Exercise } from 'app/entities/exercise.model';
import { User } from 'app/core/user/user.model';
import { EventManager } from 'app/core/util/event-manager.service';
import { MockDirective } from 'ng-mocks';
import { NgForm, NgModel } from '@angular/forms';

describe('External Submission Dialog', () => {
    let fixture: ComponentFixture<ExternalSubmissionDialogComponent>;
    let component: ExternalSubmissionDialogComponent;
    let externalSubmissionService: ExternalSubmissionService;
    let activeModal: NgbActiveModal;

    beforeEach(() => {
        activeModal = {
            dismiss: jest.fn(),
            close: jest.fn(),
        };
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExternalSubmissionDialogComponent, MockDirective(NgForm), MockDirective(NgModel)],
            providers: [{ provide: NgbActiveModal, useValue: activeModal }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExternalSubmissionDialogComponent);
                component = fixture.componentInstance;
                externalSubmissionService = TestBed.inject(ExternalSubmissionService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should get initial result on init', () => {
        const result = new Result();
        const extServiceSpy = jest.spyOn(externalSubmissionService, 'generateInitialManualResult').mockReturnValue(result);
        component.ngOnInit();
        expect(extServiceSpy).toHaveBeenCalledTimes(1);
        expect(component.result).toBe(result);
    });

    it('should dismiss the modal on clear', () => {
        component.clear();
        expect(activeModal.dismiss).toHaveBeenCalledTimes(1);
        expect(activeModal.dismiss).toHaveBeenCalledWith('cancel');
    });

    it('should save feedback correctly', () => {
        const result: Result = new Result();
        component.result = result;
        component.exercise = { id: 2 } as Exercise;
        component.student = { id: 3 } as User;

        const subject: Subject<HttpResponse<Result>> = new Subject<HttpResponse<Result>>();
        const createMock = jest.spyOn(externalSubmissionService, 'create').mockReturnValue(subject.asObservable());

        const eventManager = TestBed.inject(EventManager);
        const eventManagerSpy = jest.spyOn(eventManager, 'broadcast').mockImplementation();

        component.feedbacks = [new Feedback(), new Feedback()];

        component.feedbacks[0].type = FeedbackType.AUTOMATIC;
        component.feedbacks[0].type = FeedbackType.AUTOMATIC_ADAPTED;

        component.save();

        expect(component.isSaving).toBe(true);
        expect(result.feedbacks).toBe(component.feedbacks);
        expect(result.feedbacks).toSatisfyAll((feedback) => feedback.type === FeedbackType.MANUAL);
        expect(createMock).toHaveBeenCalledTimes(1);
        expect(createMock).toHaveBeenCalledWith(component.exercise, component.student, result);
        expect(activeModal.close).toHaveBeenCalledTimes(0);

        subject.next(new HttpResponse<Result>({ body: result }));

        expect(activeModal.close).toHaveBeenCalledTimes(1);
        expect(activeModal.close).toHaveBeenCalledWith(result);
        expect(component.isSaving).toBe(false);
        expect(eventManagerSpy).toHaveBeenCalledTimes(1);
        expect(eventManagerSpy).toHaveBeenCalledWith({ name: 'resultListModification', content: 'Added a manual result' });
    });

    it('should set isSaving to false on saveError', () => {
        component.result = new Result();
        component.isSaving = true;

        const createMock = jest.spyOn(externalSubmissionService, 'create').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        const onSaveErrorSpy = jest.spyOn(component, 'onSaveError');

        component.save();
        expect(createMock).toHaveBeenCalledTimes(1);
        expect(onSaveErrorSpy).toHaveBeenCalledTimes(1);
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
