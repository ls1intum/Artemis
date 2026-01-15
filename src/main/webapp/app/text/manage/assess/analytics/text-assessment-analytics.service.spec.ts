import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { TextAssessmentEventType } from 'app/text/shared/entities/text-assesment-event.model';
import { TextAssessmentAnalytics } from 'app/text/manage/assess/analytics/text-assessment-analytics.service';
import { FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { TextBlockType } from 'app/text/shared/entities/text-block.model';
import { TranslateService } from '@ngx-translate/core';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Params, Router } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { throwError } from 'rxjs';
import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';

/**
 * Test suite for TextAssessmentAnalytics Service.
 * Tests analytics event tracking, route parameter handling,
 * example submission filtering, and error handling.
 */
describe('TextAssessmentAnalytics Service', () => {
    setupTestBed({ zoneless: true });
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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: Router, useClass: MockRouter },
                {
                    provide: Location,
                    useValue: {
                        path(): string {
                            return '/course/1/exercise/1/participation/1/submission/1';
                        },
                    },
                },
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                LocalStorageService,
                { provide: ProfileService, useClass: MockProfileService },
                { provide: ActivatedRoute, useValue: route() },
            ],
        });
        service = TestBed.inject(TextAssessmentAnalytics);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        httpMock.verify();
    });

    it('should send assessment event if artemis analytics is enabled', () => {
        service.analyticsEnabled = true;
        service.sendAssessmentEvent(TextAssessmentEventType.EDIT_AUTOMATIC_FEEDBACK, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC);
        httpMock.expectOne({ url: `api/text/event-insights/text-assessment/events`, method: 'POST' });
    });

    it('should not send assessment event if artemis analytics is enabled', () => {
        service.analyticsEnabled = false;
        service.sendAssessmentEvent(TextAssessmentEventType.EDIT_AUTOMATIC_FEEDBACK, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC);
        httpMock.expectNone({ url: 'api/text/event-insights/text-assessment/events', method: 'POST' });
    });

    it('should not send assessment event if on example submission path', () => {
        service.analyticsEnabled = true;
        location = TestBed.inject(Location);
        const pathSpy = vi.spyOn(location, 'path').mockReturnValue('/course/1/exercise/1/participation/1/example-submissions/1');
        service.sendAssessmentEvent(TextAssessmentEventType.EDIT_AUTOMATIC_FEEDBACK, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC);
        httpMock.expectNone({ url: 'api/text/event-insights/text-assessment/events', method: 'POST' });
        expect(pathSpy).toHaveBeenCalledOnce();
    });

    it('should subscribe to route parameters if artemis analytics is enabled', () => {
        const subscribeToRouteParameters = vi.spyOn<any, any>(service, 'subscribeToRouteParameters');
        service.analyticsEnabled = true;
        service.setComponentRoute(route());
        expect(subscribeToRouteParameters).toHaveBeenCalledOnce();
        expect(service['courseId']).toBe(1);
    });

    it('should display error when submitting event to the server', () => {
        const error = new Error();
        error.message = 'error occurred';
        service.analyticsEnabled = true;
        const textAssessmentService = TestBed.inject(TextAssessmentService);
        const errorStub = vi.spyOn(textAssessmentService, 'addTextAssessmentEvent').mockReturnValue(throwError(() => error));

        service.sendAssessmentEvent(TextAssessmentEventType.EDIT_AUTOMATIC_FEEDBACK, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC);

        expect(errorStub).toHaveBeenCalledOnce();
    });

    it('should not subscribe to route parameters if artemis analytics is disabled', () => {
        const subscribeToRouteParameters = vi.spyOn<any, any>(service, 'subscribeToRouteParameters');
        service.analyticsEnabled = false;
        service.setComponentRoute(new ActivatedRoute());
        expect(subscribeToRouteParameters).not.toHaveBeenCalled();
    });
});
