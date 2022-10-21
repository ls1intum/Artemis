import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RatingListComponent } from 'app/exercises/shared/rating/rating-list/rating-list.component';
import { ArtemisTestModule } from '../../test.module';
import { RatingService } from 'app/exercises/shared/rating/rating.service';
import { ActivatedRoute, Router } from '@angular/router';
import { lastValueFrom, of } from 'rxjs';
import { Participation } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';
import { Rating } from 'app/entities/rating.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { StarRatingComponent } from 'app/exercises/shared/rating/star-rating/star-rating.component';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortService } from 'app/shared/service/sort.service';
import { MockRouter } from '../../helpers/mocks/mock-router';

describe('RatingListComponent', () => {
    let component: RatingListComponent;
    let fixture: ComponentFixture<RatingListComponent>;
    let router: Router;

    const ratings = [{ id: 1 }, { id: 2 }, { id: 3 }];
    const parentRoute = { params: of({ courseId: 123 }) } as any as ActivatedRoute;
    const route = { parent: parentRoute } as any as ActivatedRoute;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [RatingListComponent, TranslatePipeMock, MockComponent(StarRatingComponent), MockDirective(SortDirective)],
            providers: [{ provide: ActivatedRoute, useValue: route }, { provide: Router, useClass: MockRouter }, MockProvider(RatingService), MockProvider(SortService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(RatingListComponent);
                component = fixture.componentInstance;
                router = TestBed.inject(Router);

                jest.spyOn(TestBed.inject(RatingService), 'getRatingsForDashboard').mockReturnValue(of(ratings));

                component.ngOnInit();
            });
    });

    it('should initialize ratings', () => {
        expect(component.ratings).toHaveLength(3);
    });

    it('should not open exercise du to missing participation', () => {
        const rating = { id: 1, result: { id: 1 } as Result } as Rating;
        const routerNavigateSpy = jest.spyOn(router, 'navigate').mockImplementation(() => lastValueFrom(of(true)));
        component.openResult(rating);
        expect(routerNavigateSpy).not.toHaveBeenCalled();
    });

    it('should not open exercise du to missing exercise', () => {
        const rating = { id: 1, result: { id: 1, participation: { id: 1 } as Participation } as Result } as Rating;
        const routerNavigateSpy = jest.spyOn(router, 'navigate').mockImplementation(() => lastValueFrom(of(true)));
        component.openResult(rating);
        expect(routerNavigateSpy).not.toHaveBeenCalled();
    });

    it('should open exercise', () => {
        const rating = { id: 1, result: { id: 1, participation: { id: 1, exercise: { id: 1, type: ExerciseType.TEXT } as Exercise } as Participation } as Result } as Rating;
        const routerNavigateSpy = jest.spyOn(router, 'navigate').mockImplementation(() => lastValueFrom(of(true)));
        component.openResult(rating);
        expect(routerNavigateSpy).toHaveBeenCalledOnce();
    });
});
