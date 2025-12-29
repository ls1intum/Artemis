import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { RatingListComponent } from 'app/assessment/manage/rating/rating-list/rating-list.component';
import { RatingService } from 'app/assessment/shared/services/rating.service';
import { ActivatedRoute, Router } from '@angular/router';
import { lastValueFrom, of } from 'rxjs';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Rating } from 'app/assessment/shared/entities/rating.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { StarRatingComponent } from 'app/assessment/manage/rating/star-rating/star-rating.component';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortService } from 'app/shared/service/sort.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { TranslateService } from '@ngx-translate/core';

describe('RatingListComponent', () => {
    setupTestBed({ zoneless: true });
    let component: RatingListComponent;
    let fixture: ComponentFixture<RatingListComponent>;
    let router: Router;

    const ratings = [{ id: 1 }, { id: 2 }, { id: 3 }];
    const parentRoute = { params: of({ courseId: 123 }) } as any as ActivatedRoute;
    const route = { parent: parentRoute } as any as ActivatedRoute;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [RatingListComponent, TranslatePipeMock, MockComponent(StarRatingComponent), MockDirective(SortDirective)],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                MockProvider(RatingService),
                MockProvider(SortService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(RatingListComponent);
                component = fixture.componentInstance;
                router = TestBed.inject(Router);

                vi.spyOn(TestBed.inject(RatingService), 'getRatingsForDashboard').mockReturnValue(of(ratings));

                component.ngOnInit();
            });
    });

    it('should initialize ratings', () => {
        expect(component.ratings).toHaveLength(3);
    });

    it('should not open exercise due to missing participation', () => {
        const rating = { id: 1, result: { id: 1 } as Result } as Rating;
        const routerNavigateSpy = vi.spyOn(router, 'navigate').mockImplementation(() => lastValueFrom(of(true)));
        component.openResult(rating);
        expect(routerNavigateSpy).not.toHaveBeenCalled();
    });

    it('should not open exercise due to missing exercise', () => {
        const rating = { id: 1, result: { id: 1, participation: { id: 1 } as Participation } as Result } as Rating;
        const routerNavigateSpy = vi.spyOn(router, 'navigate').mockImplementation(() => lastValueFrom(of(true)));
        component.openResult(rating);
        expect(routerNavigateSpy).not.toHaveBeenCalled();
    });

    it('should open exercise', () => {
        const rating = {
            id: 1,
            result: { id: 1, submission: { participation: { id: 1, exercise: { id: 1, type: ExerciseType.TEXT } as Exercise } as Participation } } as Result,
        } as Rating;
        const routerNavigateSpy = vi.spyOn(router, 'navigate').mockImplementation(() => lastValueFrom(of(true)));
        component.openResult(rating);
        expect(routerNavigateSpy).toHaveBeenCalledTimes(1);
    });
});
