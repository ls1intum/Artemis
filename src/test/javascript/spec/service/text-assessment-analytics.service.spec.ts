import { TestBed, fakeAsync } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TextAssessmentEventType } from 'app/entities/text-assesment-event.model';
import { TextAssessmentAnalytics } from 'app/exercises/text/assess/analytics/text-assesment-analytics.service';
import { FeedbackType } from 'app/entities/feedback.model';
import { TextBlockType } from 'app/entities/text-block.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockRouter } from '../helpers/mocks/mock-router';
import { Params, Router } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { throwError } from 'rxjs';
import { Location } from '@angular/common';

describe('TextAssessmentAnalytics Service', () => {
    let service: TextAssessmentAnalytics;
    let location: Location;
    let httpMock: HttpTestingController;

    const route = (): ActivatedRoute =>
        ({
            params: {
                subscribe: (fn: (value: Params) => void) =>
                    fn({
                        courseId: 1,
                    }),
            },
        }) as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: Router, useClass: MockRouter },
                {
                    provide: Location,
                    useValue: {
                        path(): string {
                            return '/course/1/exercise/1/participation/1/submission/1';
                        },
                    },
                },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                {
                    provide: ActivatedRoute,
                    useValue: route(),
                },
            ],
        });
        service = TestBed.inject(TextAssessmentAnalytics);
        httpMock = TestBed.inject(HttpTestingController);
        httpMock.expectOne({ url: `management/info`, method: 'GET' });
    });

    afterEach(() => {
        jest.resetAllMocks();
    });

    it('should send assessment event if artemis analytics is enabled', fakeAsync(() => {
        service.analyticsEnabled = true;
        service.sendAssessmentEvent(TextAssessmentEventType.EDIT_AUTOMATIC_FEEDBACK, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC);
        httpMock.expectOne({ url: `api/event-insights/text-assessment/events`, method: 'POST' });
    }));

    it('should not send assessment event if artemis analytics is enabled', fakeAsync(() => {
        service.analyticsEnabled = false;
        service.sendAssessmentEvent(TextAssessmentEventType.EDIT_AUTOMATIC_FEEDBACK, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC);
        httpMock.expectNone({ url: 'api/event-insights/text-assessment/events', method: 'POST' });
    }));

    it('should not send assessment event if on example submission path', fakeAsync(() => {
        service.analyticsEnabled = true;
        location = TestBed.inject(Location);
        const pathSpy = jest.spyOn(location, 'path').mockReturnValue('/course/1/exercise/1/participation/1/example-submissions/1');
        service.sendAssessmentEvent(TextAssessmentEventType.EDIT_AUTOMATIC_FEEDBACK, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC);
        httpMock.expectNone({ url: 'api/event-insights/text-assessment/events', method: 'POST' });
        expect(pathSpy).toHaveBeenCalledOnce();
    }));

    it('should subscribe to route parameters if artemis analytics is enabled', fakeAsync(() => {
        const subscribeToRouteParameters = jest.spyOn<any, any>(service, 'subscribeToRouteParameters');
        service.analyticsEnabled = true;
        service.setComponentRoute(route());
        expect(subscribeToRouteParameters).toHaveBeenCalledOnce();
        expect(service['courseId']).toBe(1);
    }));

    it('should display error when submitting event to the server', () => {
        const error = new Error();
        error.message = 'error occurred';
        service.analyticsEnabled = true;
        const textAssessmentService = TestBed.inject(TextAssessmentService);
        const errorStub = jest.spyOn(textAssessmentService, 'addTextAssessmentEvent').mockReturnValue(throwError(() => error));
        const consoleErrorMock = jest.spyOn(console, 'error').mockImplementation();

        service.sendAssessmentEvent(TextAssessmentEventType.EDIT_AUTOMATIC_FEEDBACK, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC);

        expect(errorStub).toHaveBeenCalledOnce();
        expect(consoleErrorMock).toHaveBeenCalledOnce();
        expect(consoleErrorMock).toHaveBeenCalledWith('Error sending statistics: error occurred');
    });

    it('should not subscribe to route parameters if artemis analytics is disabled', fakeAsync(() => {
        const subscribeToRouteParameters = jest.spyOn<any, any>(service, 'subscribeToRouteParameters');
        service.analyticsEnabled = false;
        service.setComponentRoute(new ActivatedRoute());
        expect(subscribeToRouteParameters).not.toHaveBeenCalled();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
