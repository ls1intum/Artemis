import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LectureUnitManagementComponent } from 'app/lecture/lecture-unit/lecture-module-management/lecture-unit-management.component';

describe('LectureUnitManagementComponent', () => {
    let component: LectureUnitManagementComponent;
    let fixture: ComponentFixture<LectureUnitManagementComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [LectureUnitManagementComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(LectureUnitManagementComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
