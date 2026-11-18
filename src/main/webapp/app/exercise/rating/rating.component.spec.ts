import { Component, input, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { StarRatingChangeEvent, StarRatingComponent } from 'app/assessment/manage/rating/star-rating/star-rating.component';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockDirective } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
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
    let http: HttpTestingController;

    const ratingUrl = (resultId: number) => `/api/assessment/results/${resultId}/rating`;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [RatingComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
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
        http = TestBed.inject(HttpTestingController);

        ratingComponentFixture.componentRef.setInput('result', { id: 89 } as Result);
        ratingComponentFixture.componentRef.setInput('participation', { id: 1 } as Participation);
    });

    afterEach(() => {
        http.verify();
        vi.restoreAllMocks();
    });

    it('should get rating', () => {
        // The constructor effect loads the rating on the initial binding (replaces the former ngOnInit).
        ratingComponentFixture.detectChanges();

        const request = http.expectOne(ratingUrl(89));
        expect(request.request.method).toBe('GET');
        request.flush(3);
        expect(ratingComponent.rating()).toBe(3);
    });

    it('should return due to missing result', () => {
        ratingComponentFixture.componentRef.setInput('result', undefined);

        ratingComponentFixture.detectChanges();

        http.expectNone(() => true);
    });

    it('should return due to missing participation', () => {
        ratingComponentFixture.componentRef.setInput('participation', undefined);

        ratingComponentFixture.detectChanges();

        http.expectNone(() => true);
    });

    it('should create new local rating', () => {
        ratingComponentFixture.detectChanges();
        http.expectOne(ratingUrl(89)).flush(null);

        expect(ratingComponent.rating()).toBe(0);
    });

    it('should set rating received from server', () => {
        ratingComponentFixture.detectChanges();
        http.expectOne(ratingUrl(89)).flush(1);

        expect(ratingComponent.rating()).toBe(1);
    });

    it('should not set rating if result participation is not defined', () => {
        ratingComponentFixture.componentRef.setInput('participation', undefined);
        ratingComponentFixture.detectChanges();

        // Effect reacts to the result id change (replaces the former ngOnChanges); loadRating returns early (no participation).
        ratingComponentFixture.componentRef.setInput('result', { id: 90 } as Result);
        ratingComponentFixture.detectChanges();

        http.expectNone(() => true);
        expect(ratingComponent.rating()).toBeUndefined();
    });

    it('should call loadRating when result changes', () => {
        ratingComponentFixture.detectChanges();
        http.expectOne(ratingUrl(89)).flush(1);

        // Effect reloads when the result id changes (replaces the former ngOnChanges).
        ratingComponentFixture.componentRef.setInput('result', { id: 90 } as Result);
        ratingComponentFixture.detectChanges();
        http.expectOne(ratingUrl(90)).flush(2);

        expect(ratingComponent.rating()).toBe(2);
    });

    it('should not call loadRating if result ID remains the same', () => {
        ratingComponentFixture.componentRef.setInput('result', { id: 90 } as Result);
        ratingComponentFixture.detectChanges();
        http.expectOne(ratingUrl(90)).flush(2);

        // A new result reference with the same id must NOT trigger a reload.
        ratingComponentFixture.componentRef.setInput('result', { id: 90 } as Result);
        ratingComponentFixture.detectChanges();

        http.expectNone(() => true);
        expect(ratingComponent.rating()).toBe(2);
    });

    describe('appearance', () => {
        function renderWithStoredRating(storedRating: number | null = null) {
            ratingComponentFixture.detectChanges();
            http.expectOne(ratingUrl(89)).flush(storedRating);
            ratingComponentFixture.detectChanges();
        }

        function stars(): StarRatingComponentStub {
            return ratingComponentFixture.debugElement.query(By.directive(StarRatingComponentStub)).componentInstance;
        }

        it('should be a quiet block without a tinted box or border of its own', () => {
            renderWithStoredRating();

            const root = ratingComponentFixture.nativeElement.querySelector('.rating') as HTMLElement;
            expect(root).toBeTruthy();
            // The loud callout came from `alert alert-info` classes that the pages put on the host. The component carries none of them.
            for (const element of [ratingComponentFixture.nativeElement as HTMLElement, root]) {
                expect(element.classList.contains('alert')).toBe(false);
                expect(element.classList.contains('alert-info')).toBe(false);
            }
        });

        it('should show the prompt as a small muted label instead of bold text', () => {
            renderWithStoredRating();

            const label = ratingComponentFixture.nativeElement.querySelector('.rating__label') as HTMLElement;
            expect(label).toBeTruthy();
            expect(label.tagName.toLowerCase()).toBe('span');
            expect(label.getAttribute('jhiTranslate')).toBe('artemisApp.rating.label');
            expect(ratingComponentFixture.nativeElement.querySelector('b')).toBeNull();
        });

        it('should draw small stars in semantic colours that take colour only when chosen', () => {
            renderWithStoredRating();

            expect(stars().size()).toBe('18');
            // Unchosen stars are muted. The chosen colour is also what a hovered star shows.
            expect(stars().uncheckedColor()).toBe('var(--rating-star-muted)');
            expect(stars().checkedColor()).toBe('var(--rating-star-selected)');
            expect(stars().checkedColor()).not.toBe(stars().uncheckedColor());
        });

        it('should keep the size that a page asks for', () => {
            ratingComponentFixture.componentRef.setInput('starSize', '24');
            renderWithStoredRating();

            expect(stars().size()).toBe('24');
        });

        it('should stay interactive, so that every star can be chosen', () => {
            renderWithStoredRating();

            expect(stars().readOnly()).toBe(false);
            expect(stars().totalStars()).toBe(5);
        });

        it('should show the stored rating in the stars', () => {
            renderWithStoredRating(4);

            expect(stars().value()).toBe(4);
        });

        it('should only change the cursor while a rating is being saved, so the layout does not jump', () => {
            renderWithStoredRating();
            const root = ratingComponentFixture.nativeElement.querySelector('.rating') as HTMLElement;
            const classesBefore = Array.from(root.classList);

            ratingComponent.disableRating.set(true);
            ratingComponentFixture.detectChanges();

            expect(root.classList.contains('non-clickable')).toBe(true);
            expect(Array.from(root.classList).filter((name) => name !== 'non-clickable')).toEqual(classesBefore);
        });

        it('should put the prompt and the stars on one row with the inline layout', () => {
            ratingComponentFixture.componentRef.setInput('layout', 'inline');
            renderWithStoredRating();

            const root = ratingComponentFixture.nativeElement.querySelector('.rating') as HTMLElement;
            expect(root.classList.contains('rating--inline')).toBe(true);
        });

        it('should stack the prompt above the stars by default', () => {
            renderWithStoredRating();

            const root = ratingComponentFixture.nativeElement.querySelector('.rating') as HTMLElement;
            expect(root.classList.contains('rating--inline')).toBe(false);
        });
    });

    describe('OnRate', () => {
        beforeEach(() => {
            ratingComponent.rating.set(0);
            ratingComponentFixture.componentRef.setInput('result', { id: 89 } as Result);
        });

        it('should return', () => {
            ratingComponent.disableRating.set(true);

            ratingComponent.onRate({
                oldValue: 0,
                newValue: 2,
            });

            http.expectNone(() => true);
        });

        it('should create new rating', () => {
            ratingComponent.onRate({
                oldValue: 0,
                newValue: 2,
            });

            const request = http.expectOne(`${ratingUrl(89)}/2`);
            expect(request.request.method).toBe('POST');
            request.flush(2);
            expect(ratingComponent.rating()).toBe(2);
            expect(ratingComponent.disableRating()).toBe(false);
        });

        it('should update rating', () => {
            ratingComponent.rating.set(1);

            ratingComponent.onRate({
                oldValue: 1,
                newValue: 2,
            });

            const request = http.expectOne(`${ratingUrl(89)}/2`);
            expect(request.request.method).toBe('PUT');
            request.flush(2);
            expect(ratingComponent.rating()).toBe(2);
        });
    });
});
