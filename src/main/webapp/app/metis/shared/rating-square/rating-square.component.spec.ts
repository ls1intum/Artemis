import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { RatingSquareComponent } from 'app/metis/shared/rating-square/rating-square.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('RatingSquareComponent', () => {
    let fixture: ComponentFixture<RatingSquareComponent>;
    let translateService: TranslateService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [RatingSquareComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(RatingSquareComponent);
        translateService = TestBed.inject(TranslateService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function square(): HTMLElement {
        return fixture.nativeElement.querySelector('[data-testid="rating-square"]');
    }

    it.each([
        [1, 'lowest'],
        [2, 'low'],
        [3, 'neutral'],
        [4, 'high'],
        [5, 'highest'],
    ])('should show the rating %i of 5 in the colour of the %s step', (score, severity) => {
        fixture.componentRef.setInput('score', score);
        fixture.detectChanges();

        expect(square().textContent?.trim()).toBe(`${score}`);
        expect(square().classList.contains(`rating-square--${severity}`)).toBe(true);
        expect(square().classList.contains('rating-square--unrated')).toBe(false);
    });

    it('should follow a seven-point scale', () => {
        fixture.componentRef.setInput('points', 7);
        fixture.componentRef.setInput('score', 2);
        fixture.detectChanges();
        expect(square().classList.contains('rating-square--lowest')).toBe(true);

        fixture.componentRef.setInput('score', 6);
        fixture.detectChanges();
        expect(square().classList.contains('rating-square--highest')).toBe(true);
    });

    it('should show a dash in a dashed frame while there is no rating', () => {
        const instantSpy = vi.spyOn(translateService, 'instant');
        fixture.detectChanges();

        expect(square().textContent?.trim()).toBe('–');
        expect(square().classList.contains('rating-square--unrated')).toBe(true);
        expect(square().getAttribute('aria-label')).toBe('artemisApp.metis.ratingSquare.unrated');
        expect(instantSpy).toHaveBeenCalledWith('artemisApp.metis.ratingSquare.unrated', { score: undefined, points: 5 });
    });

    it('should name the rating and its scale for screen readers', () => {
        const instantSpy = vi.spyOn(translateService, 'instant');
        fixture.componentRef.setInput('score', 4);
        fixture.detectChanges();

        expect(square().getAttribute('role')).toBe('img');
        expect(square().getAttribute('aria-label')).toBe('artemisApp.metis.ratingSquare.label');
        expect(instantSpy).toHaveBeenCalledWith('artemisApp.metis.ratingSquare.label', { score: 4, points: 5 });
    });

    it('should shrink to the small size', () => {
        fixture.componentRef.setInput('score', 3);
        fixture.detectChanges();
        expect(square().classList.contains('rating-square--small')).toBe(false);

        fixture.componentRef.setInput('size', 'small');
        fixture.detectChanges();
        expect(square().classList.contains('rating-square--small')).toBe(true);
        expect(square().classList.contains('rating-square--neutral')).toBe(true);
    });
});
