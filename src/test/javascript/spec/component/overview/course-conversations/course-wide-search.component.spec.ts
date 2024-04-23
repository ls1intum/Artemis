import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CourseWideSearchComponent } from 'app/overview/course-conversations/course-wide-search/course-wide-search.component';

describe('CourseWideSearchComponent', () => {
    let component: CourseWideSearchComponent;
    let fixture: ComponentFixture<CourseWideSearchComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseWideSearchComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseWideSearchComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
