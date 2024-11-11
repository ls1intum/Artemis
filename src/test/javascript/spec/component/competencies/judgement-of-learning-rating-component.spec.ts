import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { JudgementOfLearningRatingComponent } from 'app/course/competencies/judgement-of-learning-rating/judgement-of-learning-rating.component';
import { AlertService } from 'app/core/util/alert.service';
import { CourseCompetencyService } from 'app/course/competencies/course-competency.service';
import { MockAlertService } from '../../helpers/mocks/service/mock-alert.service';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { StarRatingComponent } from 'app/exercises/shared/rating/star-rating/star-rating.component';
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
                {
                    provide: SessionStorageService,
                    useClass: MockSyncStorage,
                },
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
        component.courseId = undefined;
        component.rating = undefined;

        const emitSpy = jest.spyOn(component.ratingChange, 'emit');

        component.onRate({} as any);
        expect(emitSpy).not.toHaveBeenCalled();
    });

    it('should not emit value when rating is undefined', () => {
        component.courseId = 1;
        component.rating = undefined;

        const emitSpy = jest.spyOn(component.ratingChange, 'emit');

        component.onRate({} as any);
        expect(emitSpy).not.toHaveBeenCalled();
    });

    it('should not emit value when courseId is undefined', () => {
        component.courseId = undefined;
        component.rating = 3;

        const emitSpy = jest.spyOn(component.ratingChange, 'emit');

        component.onRate({} as any);
        expect(emitSpy).not.toHaveBeenCalled();
    });

    it('should emit new rating when onRate is called with valid data', fakeAsync(() => {
        component.rating = undefined;
        component.courseId = 1;

        const newRating = 4;
        const event = { oldValue: 3, newValue: newRating, starRating: {} as StarRatingComponent };
        jest.spyOn(courseCompetencyService, 'setJudgementOfLearning').mockReturnValue(of(new HttpResponse<void>({ status: 200 })));
        const emitSpy = jest.spyOn(component.ratingChange, 'emit');

        component.onRate(event);

        expect(component.rating).toBe(newRating);
        expect(emitSpy).toHaveBeenCalledWith(newRating);
    }));

    it('should show error message when setting judgement of learning fails', fakeAsync(() => {
        component.rating = undefined;
        component.courseId = 1;

        const newRating = 4;
        const event = { oldValue: 3, newValue: newRating, starRating: {} as StarRatingComponent };
        jest.spyOn(courseCompetencyService, 'setJudgementOfLearning').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        const errorSpy = jest.spyOn(alertService, 'error');

        component.onRate(event);

        expect(component.rating).toBeUndefined();
        expect(errorSpy).toHaveBeenCalled();
    }));
});
