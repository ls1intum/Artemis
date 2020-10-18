import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LectureModuleManagementComponent } from 'app/lecture/lecture-module/lecture-module-management/lecture-module-management.component';

describe('LectureModuleManagementComponent', () => {
    let component: LectureModuleManagementComponent;
    let fixture: ComponentFixture<LectureModuleManagementComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [LectureModuleManagementComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(LectureModuleManagementComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
