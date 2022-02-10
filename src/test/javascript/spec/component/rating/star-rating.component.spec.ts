import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { StarRatingComponent } from 'app/exercises/shared/rating/star-rating/star-rating.component';

describe('StarRatingComponent', () => {
    let component: StarRatingComponent;
    let fixture: ComponentFixture<StarRatingComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            declarations: [StarRatingComponent],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(StarRatingComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
        // TODO: extend test
    });
});
