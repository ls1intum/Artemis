import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockProvider, MockModule } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { TranslateModule } from '@ngx-translate/core';
import * as sinon from 'sinon';
import { Exam } from 'app/entities/exam.model';
import { By } from '@angular/platform-browser';
import { NgbModal, NgbModule, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent } from 'app/shared/components/button.component';
import { UsersImportButtonComponent } from 'app/shared/import/users-import-button.component';

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
        const modalServiceOpenStub = sinon.stub(modalService, 'open').returns(<NgbModalRef>{ componentInstance, result });

        comp.openUsersImportDialog(new MouseEvent('click'));
        const openStudentsExamImportDialogButton = fixture.debugElement.query(By.css('jhi-button'));
        expect(openStudentsExamImportDialogButton).not.toBe(null);
        expect(modalServiceOpenStub).toHaveBeenCalledTimes(1);
    });
});
