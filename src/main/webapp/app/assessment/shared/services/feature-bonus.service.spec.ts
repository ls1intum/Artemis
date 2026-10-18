import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { FeatureBonusService } from 'app/assessment/shared/services/feature-bonus.service';

describe('FeatureBonusService', () => {
    let service: FeatureBonusService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(FeatureBonusService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('addresses the awards of a participation', () => {
        const request = { title: 'Boss fight', points: 2 };
        service.getAwards(1).subscribe();
        service.createAward(1, request).subscribe();
        service.updateAward(1, 5, request).subscribe();
        service.deleteAward(1, 5).subscribe();

        httpMock.expectOne({ method: 'GET', url: 'api/assessment/participations/1/feature-bonus-awards' }).flush([]);
        const create = httpMock.expectOne({ method: 'POST', url: 'api/assessment/participations/1/feature-bonus-awards' });
        expect(create.request.body).toEqual(request);
        create.flush({ id: 5, ...request });
        httpMock.expectOne({ method: 'PUT', url: 'api/assessment/participations/1/feature-bonus-awards/5' }).flush({ id: 5, ...request });
        httpMock.expectOne({ method: 'DELETE', url: 'api/assessment/participations/1/feature-bonus-awards/5' }).flush(null);
    });

    it('reads the bonus of an exercise and of a course', () => {
        let exerciseBonus: unknown;
        let courseBonus: unknown;
        service.getFeatureBonusOfExercise(3).subscribe((bonus) => (exerciseBonus = bonus));
        service.getFeatureBonusOfCourse(4).subscribe((bonus) => (courseBonus = bonus));

        httpMock.expectOne({ method: 'GET', url: 'api/assessment/exercises/3/feature-bonus' }).flush([{ participationId: 7, points: 3, bonusPercent: 1.5 }]);
        httpMock.expectOne({ method: 'GET', url: 'api/assessment/courses/4/feature-bonus' }).flush([{ studentId: 8, points: 3 }]);
        expect(exerciseBonus).toEqual([{ participationId: 7, points: 3, bonusPercent: 1.5 }]);
        expect(courseBonus).toEqual([{ studentId: 8, points: 3 }]);
    });
});
