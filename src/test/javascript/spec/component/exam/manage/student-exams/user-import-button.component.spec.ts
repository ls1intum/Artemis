import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgbModal, NgbModalRef, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';
import { AlertService } from 'app/core/util/alert.service';
import { Exam } from 'app/entities/exam.model';
import { ButtonComponent } from 'app/shared/components/button.component';
import { UsersImportButtonComponent } from 'app/shared/import/users-import-button.component';
import { MockComponent, MockModule, MockProvider } from 'ng-mocks';

describe('UsersImportButtonComponent', () => {
    let fixture: ComponentFixture<UsersImportButtonComponent>;
    let comp: UsersImportButtonComponent;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbModule), TranslateModule.forRoot()],
            declarations: [UsersImportButtonComponent, MockComponent(ButtonComponent)],
            providers: [MockProvider(AlertService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UsersImportButtonComponent);
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

        comp.openUsersImportDialog(new MouseEvent('click'));
        const openStudentsExamImportDialogButton = fixture.debugElement.query(By.css('jhi-button'));
        expect(openStudentsExamImportDialogButton).not.toBeNull();
        expect(modalServiceOpenStub).toHaveBeenCalledOnce();
    });
});
