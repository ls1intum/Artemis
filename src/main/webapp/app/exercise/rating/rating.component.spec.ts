import { expect, vi } from 'vitest';
import { Component, input, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { StarRatingChangeEvent, StarRatingComponent } from 'app/assessment/manage/rating/star-rating/star-rating.component';
import { RatingService } from 'app/assessment/shared/services/rating.service';
import { MockRatingService } from 'test/helpers/mocks/service/mock-rating.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { of } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockDirective } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';

/**
 * Stub component for StarRatingComponent.
 * ng-mocks MockComponent cannot properly handle viewChild.required() signals in Angular 21.
 */
@Component({
    selector: 'star-rating',
    template: '',
})
class StarRatingComponentStub {
    readonly checkedColor = input<string | undefined>(undefined);
    readonly uncheckedColor = input<string | undefined>(undefined);
    readonly value = input<number>(0);
    readonly size = input<string>('24px');
    readonly readOnly = input<boolean>(false);
    readonly totalStars = input<number>(5);
    readonly rate = output<StarRatingChangeEvent>();
}

describe('RatingComponent', () => {
    setupTestBed({ zoneless: true });
    let ratingComponent: RatingComponent;
    let ratingComponentFixture: ComponentFixture<RatingComponent>;
    let ratingService: RatingService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [
                { provide: RatingService, useClass: MockRatingService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(RatingComponent, {
                remove: { imports: [TranslateDirective, StarRatingComponent] },
                add: { imports: [MockDirective(TranslateDirective), StarRatingComponentStub] },
            })
            .compileComponents()
            .then(() => {
                ratingComponentFixture = TestBed.createComponent(RatingComponent);
                ratingComponent = ratingComponentFixture.componentInstance;
                ratingService = TestBed.inject(RatingService);

                ratingComponent.result = { id: 89 } as Result;
                ratingComponentFixture.componentRef.setInput('participation', { id: 1 } as Participation);
            });
    });

    it('should get rating', () => {
        vi.spyOn(ratingService, 'getRating');
        ratingComponent.ngOnInit();
        expect(ratingService.getRating).toHaveBeenCalledOnce();
        expect(ratingComponent.result?.id).toBe(89);
    });

    it('should return due to missing result', () => {
        vi.spyOn(ratingService, 'getRating');
        delete ratingComponent.result;
        ratingComponent.ngOnInit();
        expect(ratingService.getRating).not.toHaveBeenCalled();
    });

    it('should return due to missing participation', () => {
        vi.spyOn(ratingService, 'getRating');
        ratingComponentFixture.componentRef.setInput('participation', undefined);
        // delete ratingComponent.result?.submission?.participation;
        ratingComponent.ngOnInit();
        expect(ratingService.getRating).not.toHaveBeenCalled();
    });

    it('should create new local rating', () => {
        ratingComponent.ngOnInit();
        expect(ratingComponent.rating).toBe(0);
    });

    it('should set rating received from server', () => {
        vi.spyOn(ratingService, 'getRating').mockReturnValue(of(1));
        ratingComponent.ngOnInit();
        expect(ratingComponent.rating).toBe(1);
    });

    it('should call loadRating on ngOnInit', () => {
        const loadRatingSpy = vi.spyOn(ratingComponent, 'loadRating');
        ratingComponent.ngOnInit();
        expect(loadRatingSpy).toHaveBeenCalledOnce();
    });

    it('should not set rating if result participation is not defined', () => {
        ratingComponent.result = { id: 90 } as Result;
        ratingComponentFixture.componentRef.setInput('participation', undefined);
        const loadRatingSpy = vi.spyOn(ratingComponent, 'loadRating');
        vi.spyOn(ratingService, 'getRating').mockReturnValue(of(2));
        ratingComponentFixture.detectChanges();
        expect(loadRatingSpy).toHaveBeenCalledOnce();
        expect(ratingComponent.rating).toBeUndefined();
    });

    it('should call loadRating when result changes', () => {
        const loadRatingSpy = vi.spyOn(ratingComponent, 'loadRating');
        ratingComponent.result = { id: 90 } as Result;
        vi.spyOn(ratingService, 'getRating').mockReturnValue(of(2));
        ratingComponentFixture.detectChanges();
        expect(loadRatingSpy).toHaveBeenCalledOnce();
        expect(ratingComponent.rating).toBe(2);
    });

    it('should not call loadRating if result ID remains the same', () => {
        // without this condition the loadRating might be spammed making unnecessary api calls
        const loadRatingSpy = vi.spyOn(ratingComponent, 'loadRating');
        ratingComponent.result = { id: 90 } as Result;
        vi.spyOn(ratingService, 'getRating').mockReturnValue(of(2));
        ratingComponentFixture.detectChanges();
        ratingComponent.result = { id: 90 } as Result;
        ratingComponentFixture.detectChanges();
        expect(loadRatingSpy).toHaveBeenCalledOnce();
        expect(ratingComponent.rating).toBe(2);
    });

    describe('OnRate', () => {
        beforeEach(() => {
            ratingComponent.rating = 0;
            ratingComponent.result = { id: 89 } as Result;
            vi.spyOn(ratingService, 'createRating');
            vi.spyOn(ratingService, 'updateRating');
        });

        it('should return', () => {
            ratingComponent.disableRating = true;
            ratingComponent.onRate({
                oldValue: 0,
                newValue: 2,
            });
            expect(ratingService.createRating).not.toHaveBeenCalled();
            expect(ratingService.updateRating).not.toHaveBeenCalled();
        });

        it('should create new rating', () => {
            ratingComponent.onRate({
                oldValue: 0,
                newValue: 2,
            });
            expect(ratingService.createRating).toHaveBeenCalledOnce();
            expect(ratingService.updateRating).not.toHaveBeenCalled();
            expect(ratingComponent.rating).toBe(2);
        });

        it('should update rating', () => {
            ratingComponent.rating = 1;
            ratingComponent.onRate({
                oldValue: 1,
                newValue: 2,
            });
            expect(ratingService.updateRating).toHaveBeenCalledOnce();
            expect(ratingService.createRating).not.toHaveBeenCalled();
            expect(ratingComponent.rating).toBe(2);
        });
    });
});
