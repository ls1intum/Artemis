import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CloseEditLectureModalComponent } from '../../../../../main/webapp/app/lecture/close-edit-lecture-modal/close-edit-lecture-modal.component';

describe('CloseEditLectureModalComponent', () => {
    let component: CloseEditLectureModalComponent;
    let fixture: ComponentFixture<CloseEditLectureModalComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CloseEditLectureModalComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CloseEditLectureModalComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
