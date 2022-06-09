import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RatingComponent } from 'app/exercises/shared/rating/rating.component';
import { ArtemisTestModule } from '../../test.module';
import { StarRatingComponent } from 'app/exercises/shared/rating/star-rating/star-rating.component';
import { RatingService } from 'app/exercises/shared/rating/rating.service';
import { MockRatingService } from '../../helpers/mocks/service/mock-rating.service';
import { Result } from 'app/entities/result.model';
import { Submission } from 'app/entities/submission.model';
import { Rating } from 'app/entities/rating.model';
import { of } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { Participation } from 'app/entities/participation/participation.model';
import { MockComponent } from 'ng-mocks';

describe('RatingComponent', () => {
    let ratingComponent: RatingComponent;
    let ratingComponentFixture: ComponentFixture<RatingComponent>;
    let ratingService: RatingService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [RatingComponent, MockComponent(StarRatingComponent)],
            providers: [
                { provide: RatingService, useClass: MockRatingService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                ratingComponentFixture = TestBed.createComponent(RatingComponent);
                ratingComponent = ratingComponentFixture.componentInstance;
                ratingService = TestBed.inject(RatingService);

                ratingComponent.result = { id: 89 } as Result;
                ratingComponent.result.submission = { id: 1 } as Submission;
                ratingComponent.result.participation = { id: 1 } as Participation;
            });
    });

    it('should get rating', () => {
        jest.spyOn(ratingService, 'getRating');
        ratingComponent.ngOnInit();
        expect(ratingService.getRating).toHaveBeenCalledOnce();
        expect(ratingComponent.result?.id).toBe(89);
    });

    it('should return due to missing result', () => {
        jest.spyOn(ratingService, 'getRating');
        delete ratingComponent.result;
        ratingComponent.ngOnInit();
        expect(ratingService.getRating).toHaveBeenCalledTimes(0);
    });

    it('should return due to missing participation', () => {
        jest.spyOn(ratingService, 'getRating');
        delete ratingComponent.result?.participation;
        ratingComponent.ngOnInit();
        expect(ratingService.getRating).toHaveBeenCalledTimes(0);
    });

    it('should create new local rating', () => {
        ratingComponent.ngOnInit();
        expect(ratingComponent.rating.result?.id).toBe(89);
        expect(ratingComponent.rating.rating).toBe(0);
    });

    it('should set rating received from server', () => {
        jest.spyOn(ratingService, 'getRating').mockReturnValue(of(new Rating({ id: 90 } as Result, 1)));
        ratingComponent.ngOnInit();
        expect(ratingComponent.rating.result?.id).toBe(90);
        expect(ratingComponent.rating.rating).toBe(1);
    });

    describe('OnRate', () => {
        beforeEach(() => {
            ratingComponent.rating = new Rating({ id: 89 } as Result, 0);
            jest.spyOn(ratingService, 'createRating');
            jest.spyOn(ratingService, 'updateRating');
        });

        it('should return', () => {
            ratingComponent.disableRating = true;
            ratingComponent.onRate({
                oldValue: 0,
                newValue: 2,
                starRating: new StarRatingComponent(),
            });
            expect(ratingService.createRating).toHaveBeenCalledTimes(0);
            expect(ratingService.updateRating).toHaveBeenCalledTimes(0);
        });

        it('should create new rating', () => {
            ratingComponent.onRate({
                oldValue: 0,
                newValue: 2,
                starRating: new StarRatingComponent(),
            });
            expect(ratingService.createRating).toHaveBeenCalledOnce();
            expect(ratingService.updateRating).toHaveBeenCalledTimes(0);
            expect(ratingComponent.rating.result?.id).toBe(89);
            expect(ratingComponent.rating.rating).toBe(2);
        });

        it('should update rating', () => {
            ratingComponent.rating.id = 89;
            ratingComponent.onRate({
                oldValue: 0,
                newValue: 2,
                starRating: new StarRatingComponent(),
            });
            expect(ratingService.updateRating).toHaveBeenCalledOnce();
            expect(ratingService.createRating).toHaveBeenCalledTimes(0);
            expect(ratingComponent.rating.result?.id).toBe(89);
            expect(ratingComponent.rating.rating).toBe(2);
        });
    });
});
