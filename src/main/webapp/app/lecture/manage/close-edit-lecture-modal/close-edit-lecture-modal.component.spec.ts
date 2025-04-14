import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CloseEditLectureModalComponent } from 'app/lecture/manage/close-edit-lecture-modal/close-edit-lecture-modal.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CloseEditLectureModalComponent', () => {
    let component: CloseEditLectureModalComponent;
    let fixture: ComponentFixture<CloseEditLectureModalComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CloseEditLectureModalComponent],
            providers: [MockProvider(NgbActiveModal), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(CloseEditLectureModalComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
