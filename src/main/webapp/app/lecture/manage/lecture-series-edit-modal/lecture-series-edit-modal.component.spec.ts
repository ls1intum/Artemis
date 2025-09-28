import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LectureSeriesEditModalComponent } from './lecture-series-edit-modal.component';

describe('LectureSeriesEditModal', () => {
    let component: LectureSeriesEditModalComponent;
    let fixture: ComponentFixture<LectureSeriesEditModalComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LectureSeriesEditModalComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(LectureSeriesEditModalComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
