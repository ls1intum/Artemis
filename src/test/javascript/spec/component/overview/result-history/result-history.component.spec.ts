import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResultHistoryComponent } from 'app/overview/result-history/result-history.component';
import { MockPipe } from 'ng-mocks';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTestModule } from '../../../test.module';
import { Result } from 'app/entities/result.model';
import { SelfLearningFeedbackRequest } from 'app/entities/self-learning-feedback-request.model';

describe('ResultHistoryComponent', () => {
    let component: ResultHistoryComponent;
    let fixture: ComponentFixture<ResultHistoryComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ResultHistoryComponent, MockPipe(ArtemisDatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ResultHistoryComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize with same rated results', () => {
        component.entries = [createResult(1, true), createResult(2, true), createResult(3, true)];
        component.ngOnChanges();
        expect(component.displayedEntries).toEqual([createResult(1, true), createResult(2, true), createResult(3, true)]);
        expect(component.showPreviousDivider).toBeFalse();
        expect(component.movedLastRatedResult).toBeFalsy();

        component.entries = [createResult(1, false), createResult(2, false), createResult(3, false), createResult(4, false), createResult(5, false), createResult(6, false)];
        component.ngOnChanges();
        expect(component.displayedEntries).toEqual([createResult(2, false), createResult(3, false), createResult(4, false), createResult(5, false), createResult(6, false)]);
        expect(component.showPreviousDivider).toBeTrue();
        expect(component.movedLastRatedResult).toBeFalsy();
    });

    it('should initialize with mixed rated results and self-learning-feedback-requests', () => {
        component.entries = [createResult(1, true), createResult(2, false), createResult(3, false), createSelfLearningFeedbackRequests(7), createSelfLearningFeedbackRequests(8)];
        component.ngOnChanges();
        expect(component.displayedEntries).toEqual([
            createResult(1, true),
            createResult(2, false),
            createResult(3, false),
            createSelfLearningFeedbackRequests(7),
            createSelfLearningFeedbackRequests(8),
        ]);
        expect(component.showPreviousDivider).toBeFalse();
        expect(component.movedLastRatedResult).toBeFalsy();

        component.entries = [
            createResult(1, true),
            createResult(2, false),
            createResult(3, false),
            createResult(4, false),
            createResult(5, false),
            createResult(6, false),
            createSelfLearningFeedbackRequests(7),
            createSelfLearningFeedbackRequests(8),
        ];
        component.ngOnChanges();
        expect(component.displayedEntries).toEqual([
            createResult(1, true),
            createResult(5, false),
            createResult(6, false),
            createSelfLearningFeedbackRequests(7),
            createSelfLearningFeedbackRequests(8),
        ]);
        expect(component.showPreviousDivider).toBeTrue();
        expect(component.movedLastRatedResult).toBeTrue();
    });

    function createResult(id: number, rated: boolean): Result {
        const res = new Result();
        res.id = id;
        res.rated = rated;
        return res;
    }

    function createSelfLearningFeedbackRequests(id: number): SelfLearningFeedbackRequest {
        const request = new SelfLearningFeedbackRequest();
        request.id = id;
        request.requestDateTime = '2024-05-05T13:50:59.540Z';
        request.result = undefined;
        return request;
    }
});
