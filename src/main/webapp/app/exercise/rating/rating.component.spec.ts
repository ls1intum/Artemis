import { Component, input, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { StarRatingChangeEvent, StarRatingComponent } from 'app/assessment/manage/rating/star-rating/star-rating.component';
import { RatingService } from 'app/assessment/shared/services/rating.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockDirective } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockRatingService } from 'test/helpers/mocks/service/mock-rating.service';
import { By } from '@angular/platform-browser';

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
    let ratingComponent: RatingComponent;
    let ratingComponentFixture: ComponentFixture<RatingComponent>;
    let ratingService: RatingService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [RatingComponent],
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
            .compileComponents();

        ratingComponentFixture = TestBed.createComponent(RatingComponent);
        ratingComponent = ratingComponentFixture.componentInstance;
        ratingService = TestBed.inject(RatingService);

        ratingComponentFixture.componentRef.setInput('result', { id: 89 } as Result);
        ratingComponentFixture.componentRef.setInput('participation', { id: 1 } as Participation);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should get rating', () => {
        const getRatingSpy = vi.spyOn(ratingService, 'getRating');

        // The constructor effect loads the rating on the initial binding (replaces the former ngOnInit).
        ratingComponentFixture.detectChanges();

        expect(getRatingSpy).toHaveBeenCalledTimes(1);
        expect(ratingComponent.result()?.id).toBe(89);
    });

    it('should return due to missing result', () => {
        const getRatingSpy = vi.spyOn(ratingService, 'getRating');
        ratingComponentFixture.componentRef.setInput('result', undefined);

        ratingComponentFixture.detectChanges();

        expect(getRatingSpy).not.toHaveBeenCalled();
    });

    it('should return due to missing participation', () => {
        const getRatingSpy = vi.spyOn(ratingService, 'getRating');
        ratingComponentFixture.componentRef.setInput('participation', undefined);

        ratingComponentFixture.detectChanges();

        expect(getRatingSpy).not.toHaveBeenCalled();
    });

    it('should create new local rating', () => {
        ratingComponentFixture.detectChanges();

        expect(ratingComponent.rating()).toBe(0);
    });

    it('should set rating received from server', () => {
        vi.spyOn(ratingService, 'getRating').mockReturnValue(of(1));

        ratingComponentFixture.detectChanges();

        expect(ratingComponent.rating()).toBe(1);
    });

    it('should load the rating on init', () => {
        const loadRatingSpy = vi.spyOn(ratingComponent, 'loadRating');

        ratingComponentFixture.detectChanges();

        expect(loadRatingSpy).toHaveBeenCalledTimes(1);
    });

    it('should not set rating if result participation is not defined', () => {
        ratingComponentFixture.componentRef.setInput('participation', undefined);
        vi.spyOn(ratingService, 'getRating').mockReturnValue(of(2));
        // First CD runs ngOnInit + the constructor effect once for the beforeEach result (id 89).
        ratingComponentFixture.detectChanges();
        const loadRatingSpy = vi.spyOn(ratingComponent, 'loadRating');

        // Effect reacts to the result id change (replaces the former ngOnChanges); loadRating returns early (no participation).
        ratingComponentFixture.componentRef.setInput('result', { id: 90 } as Result);
        ratingComponentFixture.detectChanges();

        expect(loadRatingSpy).toHaveBeenCalledTimes(1);
        expect(ratingComponent.rating()).toBeUndefined();
    });

    it('should call loadRating when result changes', () => {
        vi.spyOn(ratingService, 'getRating').mockReturnValue(of(2));
        // First CD runs ngOnInit + the constructor effect once for the beforeEach result (id 89).
        ratingComponentFixture.detectChanges();
        const loadRatingSpy = vi.spyOn(ratingComponent, 'loadRating');

        // Effect reloads when the result id changes (replaces the former ngOnChanges).
        ratingComponentFixture.componentRef.setInput('result', { id: 90 } as Result);
        ratingComponentFixture.detectChanges();

        expect(loadRatingSpy).toHaveBeenCalledTimes(1);
        expect(ratingComponent.rating()).toBe(2);
    });

    it('should not call loadRating if result ID remains the same', () => {
        vi.spyOn(ratingService, 'getRating').mockReturnValue(of(2));
        ratingComponentFixture.componentRef.setInput('result', { id: 90 } as Result);
        // First CD runs ngOnInit + the effect once for result id 90 (previousResultId becomes 90).
        ratingComponentFixture.detectChanges();
        const loadRatingSpy = vi.spyOn(ratingComponent, 'loadRating');

        // A new result reference with the same id must NOT trigger a reload.
        ratingComponentFixture.componentRef.setInput('result', { id: 90 } as Result);
        ratingComponentFixture.detectChanges();

        expect(loadRatingSpy).not.toHaveBeenCalled();
        expect(ratingComponent.rating()).toBe(2);
    });

    describe('appearance', () => {
        function stars(): StarRatingComponentStub {
            return ratingComponentFixture.debugElement.query(By.directive(StarRatingComponentStub)).componentInstance;
        }

        it('should be a quiet block without a tinted box or border of its own', () => {
            ratingComponentFixture.detectChanges();

            const root = ratingComponentFixture.nativeElement.querySelector('.rating') as HTMLElement;
            expect(root).toBeTruthy();
            // The loud callout came from `alert alert-info` classes that the pages put on the host. The component carries none of them.
            for (const element of [ratingComponentFixture.nativeElement as HTMLElement, root]) {
                expect(element.classList.contains('alert')).toBe(false);
                expect(element.classList.contains('alert-info')).toBe(false);
            }
        });

        it('should show the prompt as a small muted label instead of bold text', () => {
            ratingComponentFixture.detectChanges();

            const label = ratingComponentFixture.nativeElement.querySelector('.rating__label') as HTMLElement;
            expect(label).toBeTruthy();
            expect(label.tagName.toLowerCase()).toBe('span');
            expect(label.getAttribute('jhiTranslate')).toBe('artemisApp.rating.label');
            expect(ratingComponentFixture.nativeElement.querySelector('b')).toBeNull();
        });

        it('should draw small stars in semantic colours that take colour only when chosen', () => {
            ratingComponentFixture.detectChanges();

            expect(stars().size()).toBe('18');
            // Unchosen stars are muted. The chosen colour is also what a hovered star shows.
            expect(stars().uncheckedColor()).toBe('var(--rating-star-muted)');
            expect(stars().checkedColor()).toBe('var(--rating-star-selected)');
            expect(stars().checkedColor()).not.toBe(stars().uncheckedColor());
        });

        it('should keep the size that a page asks for', () => {
            ratingComponentFixture.componentRef.setInput('starSize', '24');
            ratingComponentFixture.detectChanges();

            expect(stars().size()).toBe('24');
        });

        it('should stay interactive, so that every star can be chosen', () => {
            ratingComponentFixture.detectChanges();

            expect(stars().readOnly()).toBe(false);
            expect(stars().totalStars()).toBe(5);
        });

        it('should show the stored rating in the stars', () => {
            vi.spyOn(ratingService, 'getRating').mockReturnValue(of(4));

            ratingComponentFixture.detectChanges();
            ratingComponentFixture.detectChanges();

            expect(stars().value()).toBe(4);
        });

        it('should only change the cursor while a rating is being saved, so the layout does not jump', () => {
            ratingComponentFixture.detectChanges();
            const root = ratingComponentFixture.nativeElement.querySelector('.rating') as HTMLElement;
            const classesBefore = Array.from(root.classList);

            ratingComponent.disableRating.set(true);
            ratingComponentFixture.detectChanges();

            expect(root.classList.contains('non-clickable')).toBe(true);
            expect(Array.from(root.classList).filter((name) => name !== 'non-clickable')).toEqual(classesBefore);
        });

        it('should put the prompt and the stars on one row with the inline layout', () => {
            ratingComponentFixture.componentRef.setInput('layout', 'inline');
            ratingComponentFixture.detectChanges();

            const root = ratingComponentFixture.nativeElement.querySelector('.rating') as HTMLElement;
            expect(root.classList.contains('rating--inline')).toBe(true);
        });

        it('should stack the prompt above the stars by default', () => {
            ratingComponentFixture.detectChanges();

            const root = ratingComponentFixture.nativeElement.querySelector('.rating') as HTMLElement;
            expect(root.classList.contains('rating--inline')).toBe(false);
        });
    });

    describe('OnRate', () => {
        beforeEach(() => {
            ratingComponent.rating.set(0);
            ratingComponentFixture.componentRef.setInput('result', { id: 89 } as Result);
            vi.spyOn(ratingService, 'createRating');
            vi.spyOn(ratingService, 'updateRating');
        });

        it('should return', () => {
            ratingComponent.disableRating.set(true);

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

            expect(ratingService.createRating).toHaveBeenCalledTimes(1);
            expect(ratingService.updateRating).not.toHaveBeenCalled();
            expect(ratingComponent.rating()).toBe(2);
        });

        it('should update rating', () => {
            ratingComponent.rating.set(1);

            ratingComponent.onRate({
                oldValue: 1,
                newValue: 2,
            });

            expect(ratingService.updateRating).toHaveBeenCalledTimes(1);
            expect(ratingService.createRating).not.toHaveBeenCalled();
            expect(ratingComponent.rating()).toBe(2);
        });
    });
});
