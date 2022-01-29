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
import { Router } from '@angular/router';
import { ActivatedRoute } from '@angular/router';

describe('TextAssessmentAnalytics Service', () => {
    let service: TextAssessmentAnalytics;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: Router, useClass: MockRouter },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        });
        service = TestBed.inject(TextAssessmentAnalytics);
        httpMock = TestBed.inject(HttpTestingController);
        httpMock.expectOne({ url: `${SERVER_API_URL}management/info`, method: 'GET' });
    });

    it('should send assessment event if artemis analytics is enabled', fakeAsync(() => {
        service.analyticsEnabled = true;
        service.sendAssessmentEvent(TextAssessmentEventType.VIEW_AUTOMATIC_SUGGESTION_ORIGIN, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC);
        httpMock.expectOne({ url: `${SERVER_API_URL}/api/analytics/text-assessment/events`, method: 'POST' });
    }));

    it('should not send assessment event if artemis analytics is enabled', fakeAsync(() => {
        service.analyticsEnabled = false;
        service.sendAssessmentEvent(TextAssessmentEventType.VIEW_AUTOMATIC_SUGGESTION_ORIGIN, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC);
        httpMock.expectNone({ url: `${SERVER_API_URL}/api/analytics/text-assessment/events`, method: 'POST' });
    }));

    it('should subscribe to route parameters if artemis analytics is enabled', fakeAsync(() => {
        const subscribeToRouteParameters = jest.spyOn<any, any>(service, 'subscribeToRouteParameters').mockImplementation(() => {});
        service.analyticsEnabled = true;
        service.setComponentRoute(new ActivatedRoute());
        expect(subscribeToRouteParameters).toHaveBeenCalledTimes(1);
    }));

    it('should not subscribe to route parameters if artemis analytics is disabled', fakeAsync(() => {
        const subscribeToRouteParameters = jest.spyOn<any, any>(service, 'subscribeToRouteParameters');
        service.analyticsEnabled = false;
        service.setComponentRoute(new ActivatedRoute());
        expect(subscribeToRouteParameters).toHaveBeenCalledTimes(0);
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
