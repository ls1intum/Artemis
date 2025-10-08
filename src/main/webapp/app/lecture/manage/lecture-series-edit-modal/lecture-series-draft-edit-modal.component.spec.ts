import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LectureSeriesDraftEditModalComponent } from './lecture-series-draft-edit-modal.component';

describe('LectureSeriesEditModal', () => {
    let component: LectureSeriesDraftEditModalComponent;
    let fixture: ComponentFixture<LectureSeriesDraftEditModalComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LectureSeriesDraftEditModalComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(LectureSeriesDraftEditModalComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
