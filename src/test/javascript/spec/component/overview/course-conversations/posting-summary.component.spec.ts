import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PostingSummaryComponent } from '../../../../../../main/webapp/app/overview/course-conversations/posting-summary/posting-summary.component';

describe('PostingSummaryComponent', () => {
    let component: PostingSummaryComponent;
    let fixture: ComponentFixture<PostingSummaryComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PostingSummaryComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(PostingSummaryComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
