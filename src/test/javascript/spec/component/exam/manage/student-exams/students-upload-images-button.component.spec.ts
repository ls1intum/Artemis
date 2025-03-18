import { StudentsUploadImagesButtonComponent } from 'app/exam/manage/students/upload-images/students-upload-images-button.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockModule, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateModule } from '@ngx-translate/core';
import { Exam } from 'app/entities/exam/exam.model';
import { By } from '@angular/platform-browser';
import { NgbModal, NgbModalRef, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent } from 'app/shared/components/button.component';
import { input } from '@angular/core';

describe('StudentsUploadImagesButtonComponent', () => {
    let fixture: ComponentFixture<StudentsUploadImagesButtonComponent>;
    let comp: StudentsUploadImagesButtonComponent;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbModule), TranslateModule.forRoot()],
            declarations: [StudentsUploadImagesButtonComponent, MockComponent(ButtonComponent)],
            providers: [MockProvider(AlertService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StudentsUploadImagesButtonComponent);
                comp = fixture.componentInstance;
                modalService = TestBed.inject(NgbModal);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        const componentInstance = { courseId: Number, exam: Exam };
        const result = new Promise((resolve) => resolve(true));
        const modalServiceOpenStub = jest.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{ componentInstance, result });

        TestBed.runInInjectionContext(() => {
            comp.exam = input({} as Exam);
            comp.courseId = input(1);
        });
        comp.openUploadImagesDialog(new MouseEvent('click'));

        const openUploadImagesDialogButton = fixture.debugElement.query(By.css('jhi-button'));
        expect(openUploadImagesDialogButton).not.toBeNull();
        expect(modalServiceOpenStub).toHaveBeenCalledOnce();
    });
});
