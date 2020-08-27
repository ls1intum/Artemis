import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { RatingListComponent } from 'app/exercises/shared/rating/rating-list/rating-list.component';

describe('RatingListComponent', () => {
    let component: RatingListComponent;
    let fixture: ComponentFixture<RatingListComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            declarations: [RatingListComponent],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(RatingListComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
