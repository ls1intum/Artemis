import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CourseTutorialGroupDetailComponent } from './course-tutorial-group-detail.component';

describe('NewTutorialGroupDetail', () => {
    let component: CourseTutorialGroupDetailComponent;
    let fixture: ComponentFixture<CourseTutorialGroupDetailComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseTutorialGroupDetailComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseTutorialGroupDetailComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
