import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { JudgementOfLearningRatingComponent } from 'app/atlas/overview/judgement-of-learning-rating/judgement-of-learning-rating.component';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

describe('JudgementOfLearningRatingComponent', () => {
    let component: JudgementOfLearningRatingComponent;
    let fixture: ComponentFixture<JudgementOfLearningRatingComponent>;

    let courseCompetencyService: CourseCompetencyService;
    let alertService: AlertService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [JudgementOfLearningRatingComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                SessionStorageService,
            ],
        }).compileComponents();

        courseCompetencyService = TestBed.inject(CourseCompetencyService);
        alertService = TestBed.inject(AlertService);

        fixture = TestBed.createComponent(JudgementOfLearningRatingComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('competencyId', 2);
        fixture.componentRef.setInput('rating', 3);
        fixture.componentRef.setInput('mastery', 0.8);
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    it('should not emit value when rating and courseId is undefined', () => {
        fixture.componentRef.setInput('courseId', undefined as any);
        fixture.componentRef.setInput('rating', undefined);

        component.onRate({} as any);
        expect(component.rating()).toBeUndefined();
    });

    it('should not emit value when rating is undefined', () => {
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('rating', undefined);
        component.onRate({} as any);
        expect(component.rating()).toBeUndefined();
    });

    it('should not emit value when courseId is undefined', () => {
        fixture.componentRef.setInput('courseId', undefined as any);
        fixture.componentRef.setInput('rating', 3);
        component.onRate({} as any);
        expect(component.rating()).toBe(3);
    });

    it('should emit new rating when onRate is called with valid data', fakeAsync(() => {
        fixture.componentRef.setInput('rating', undefined);
        fixture.componentRef.setInput('courseId', 1);

        const newRating = 4;
        const event = { oldValue: 3, newValue: newRating };
        jest.spyOn(courseCompetencyService, 'setJudgementOfLearning').mockReturnValue(of(new HttpResponse<void>({ status: 200 })));
        component.onRate(event);
        expect(component.rating()).toBe(newRating);
    }));

    it('should show error message when setting judgement of learning fails', fakeAsync(() => {
        fixture.componentRef.setInput('rating', undefined);
        fixture.componentRef.setInput('courseId', 1);

        const newRating = 4;
        const event = { oldValue: 3, newValue: newRating };
        jest.spyOn(courseCompetencyService, 'setJudgementOfLearning').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        const errorSpy = jest.spyOn(alertService, 'error');

        component.onRate(event);

        expect(component.rating()).toBeUndefined();
        expect(errorSpy).toHaveBeenCalled();
    }));
});
