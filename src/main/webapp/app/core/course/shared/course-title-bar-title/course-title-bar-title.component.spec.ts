import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CourseTitleBarTitleComponent } from './course-title-bar-title.component';

describe('CourseTitleBarTitleComponent', () => {
    let component: CourseTitleBarTitleComponent;
    let fixture: ComponentFixture<CourseTitleBarTitleComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseTitleBarTitleComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseTitleBarTitleComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
