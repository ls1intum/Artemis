import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockModule, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateModule } from '@ngx-translate/core';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { By } from '@angular/platform-browser';
import { NgbModal, NgbModalRef, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { StudentsRoomDistributionButtonComponent } from 'app/exam/manage/students/room-distribution/students-room-distribution-button.component';

describe('StudentsRoomDistributionButtonComponent', () => {
    let fixture: ComponentFixture<StudentsRoomDistributionButtonComponent>;
    let comp: StudentsRoomDistributionButtonComponent;
    let modalService: NgbModal;
    const testExam: Exam = { id: 1, title: 'Test Exam', course: { id: 1 } };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbModule), TranslateModule.forRoot()],
            declarations: [StudentsRoomDistributionButtonComponent, MockComponent(ButtonComponent)],
            providers: [MockProvider(AlertService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StudentsRoomDistributionButtonComponent);
                comp = fixture.componentInstance;
                modalService = TestBed.inject(NgbModal);
                fixture.componentRef.setInput('courseId', 1);
                fixture.componentRef.setInput('exam', testExam);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize and set modal inputs correctly', () => {
        const mockModalRef = {
            componentInstance: { courseId: undefined as any, exam: undefined as any },
            result: new Promise((resolve) => resolve(true)),
        };
        const modalServiceOpenStub = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);

        comp.openRoomDistributionDialog(new MouseEvent('click'));

        const openRoomDistributionDialogButton = fixture.debugElement.query(By.css('jhi-button'));
        expect(openRoomDistributionDialogButton).not.toBeNull();
        expect(modalServiceOpenStub).toHaveBeenCalledOnce();
        expect(mockModalRef.componentInstance.courseId()).toBe(1);
        expect(mockModalRef.componentInstance.exam()).toBe(testExam);
    });
});
