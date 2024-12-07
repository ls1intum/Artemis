import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CloseEditLectureDialogComponent } from './close-edit-lecture-dialog.component';

describe('CloseEditLectureDialogComponentTsComponent', () => {
    let component: CloseEditLectureDialogComponent;
    let fixture: ComponentFixture<CloseEditLectureDialogComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CloseEditLectureDialogComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CloseEditLectureDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
