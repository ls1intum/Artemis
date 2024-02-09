import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import {
    mockClientMessage,
    mockConversation,
    mockExercisePlan,
    mockExercisePlanStep,
    mockPlanConversation,
    mockServerMessage,
    mockServerPlanMessage,
} from '../../helpers/sample/iris-sample-data';
import { IrisHttpCodeEditorMessageService } from 'app/iris/http-code-editor-message.service';
import { IrisUserMessage } from 'app/entities/iris/iris-message.model';

describe('Iris Http Code Editor Message Service', () => {
    let service: IrisHttpCodeEditorMessageService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [IrisHttpCodeEditorMessageService],
        });
        service = TestBed.inject(IrisHttpCodeEditorMessageService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    describe('Service methods', () => {
        it('should create a message', fakeAsync(() => {
            const returnedFromService = { ...mockClientMessage, id: 0 };
            const expected = { ...returnedFromService, id: 0 };
            service
                .createMessage(2, new IrisUserMessage())
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should return all messages for a session', fakeAsync(() => {
            const returnedFromService = [mockClientMessage, mockServerMessage];
            const expected = returnedFromService;
            service
                .getMessages(mockConversation.id)
                .pipe(take(2))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should update component plan instruction field', fakeAsync(() => {
            const returnedFromService = { ...mockExercisePlanStep, instructions: 'I will add a QuickSort algorithm task.' };
            const expected = returnedFromService;
            service
                .updateExercisePlanStepInstructions(mockPlanConversation.id, mockServerPlanMessage.id, mockExercisePlan.id!, mockExercisePlanStep.id!, returnedFromService)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should execute the exercise component plans', fakeAsync(() => {
            const returnedFromService = { ...mockExercisePlanStep };
            //const expected = { ...returnedFromService, id: 0 };
            service
                .executePlanStep(mockPlanConversation.id, mockServerPlanMessage.id, mockExercisePlan.id!, mockExercisePlanStep.id!)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.ok).toBeTrue());
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
        }));

        afterEach(() => {
            httpMock.verify();
        });
    });
});
