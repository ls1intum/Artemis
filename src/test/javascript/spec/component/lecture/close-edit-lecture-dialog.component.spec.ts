import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CloseEditLectureDialogComponent } from 'app/lecture/close-edit-lecture-dialog.component.ts/close-edit-lecture-dialog.component';
import { ArtemisTestModule } from '../../test.module';

describe('CloseEditLectureDialogComponent', () => {
    let component: CloseEditLectureDialogComponent;
    let fixture: ComponentFixture<CloseEditLectureDialogComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule, CloseEditLectureDialogComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CloseEditLectureDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
