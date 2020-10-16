import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LectureComponentManagementComponent } from 'app/lecture/lecture-component/lecture-component-management/lecture-component-management.component';

describe('LectureComponentManagementComponent', () => {
    let component: LectureComponentManagementComponent;
    let fixture: ComponentFixture<LectureComponentManagementComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [LectureComponentManagementComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(LectureComponentManagementComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
