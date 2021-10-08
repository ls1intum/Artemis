import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { MockComponent, MockProvider, MockModule } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { TranslateModule } from '@ngx-translate/core';
import * as sinon from 'sinon';
import { Exam } from 'app/entities/exam.model';
import { By } from '@angular/platform-browser';
import { NgbModal, NgbModule, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent } from 'app/shared/components/button.component';
import { StudentsImportButtonComponent } from 'app/shared/import/students-import-button.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('StudentsImportButtonComponent', () => {
    let fixture: ComponentFixture<StudentsImportButtonComponent>;
    let comp: StudentsImportButtonComponent;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbModule), TranslateModule.forRoot()],
            declarations: [StudentsImportButtonComponent, MockComponent(ButtonComponent)],
            providers: [MockProvider(AlertService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StudentsImportButtonComponent);
                comp = fixture.componentInstance;
                modalService = TestBed.inject(NgbModal);
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should initialize', () => {
        const componentInstance = { courseId: Number, exam: Exam };
        const result = new Promise((resolve) => resolve(true));
        const modalServiceOpenStub = sinon.stub(modalService, 'open').returns(<NgbModalRef>{ componentInstance, result });

        comp.openStudentsImportDialog(new MouseEvent('click'));

        const openStudentsExamImportDialogButton = fixture.debugElement.query(By.css('jhi-button'));
        expect(openStudentsExamImportDialogButton).to.exist;
        expect(modalServiceOpenStub).to.have.been.called;
        modalServiceOpenStub.restore();
    });
});
