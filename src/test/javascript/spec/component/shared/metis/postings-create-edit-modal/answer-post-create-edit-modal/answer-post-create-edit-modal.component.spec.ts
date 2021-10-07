import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import * as sinon from 'sinon';
import { SinonSpy, spy } from 'sinon';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { MockNgbModalService } from '../../../../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { PostingsMarkdownEditorComponent } from 'app/shared/metis/postings-markdown-editor/postings-markdown-editor.component';
import { PostingsButtonComponent } from 'app/shared/metis/postings-button/postings-button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { metisAnswerPostToCreateUser1, metisAnswerPostUser1, metisAnswerPostUser2 } from '../../../../../helpers/sample/metis-sample-data';

chai.use(sinonChai);
const expect = chai.expect;

describe('AnswerPostCreateEditModalComponent', () => {
    let component: AnswerPostCreateEditModalComponent;
    let fixture: ComponentFixture<AnswerPostCreateEditModalComponent>;
    let metisService: MetisService;
    let modal: MockNgbModalService;
    let createPostingSpy: SinonSpy;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(FormsModule), MockModule(ReactiveFormsModule)],
            providers: [FormBuilder, { provide: MetisService, useClass: MockMetisService }],
            declarations: [
                AnswerPostCreateEditModalComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(PostingsMarkdownEditorComponent),
                MockComponent(PostingsButtonComponent),
                MockComponent(HelpIconComponent),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AnswerPostCreateEditModalComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                modal = TestBed.inject(NgbModal);
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should init modal with correct content and title for answer post with id', () => {
        component.posting = metisAnswerPostUser1;
        component.ngOnInit();
        expect(component.modalTitle).to.be.equal('artemisApp.metis.editPosting');
        expect(component.content).to.be.equal(metisAnswerPostUser1.content);
    });

    it('should init modal with correct content and title for answer post without id', () => {
        component.posting = metisAnswerPostToCreateUser1;
        component.ngOnInit();
        expect(component.modalTitle).to.be.equal('artemisApp.metis.createModalTitleAnswer');
        expect(component.content).to.be.equal(metisAnswerPostToCreateUser1.content);
    });

    it('should invoke the modalService', () => {
        const componentInstance = { title: String, content: String };
        const result = new Promise((resolve) => resolve(true));
        const modalServiceOpenStub = sinon.stub(modal, 'open').returns(<NgbModalRef>{
            componentInstance,
            result,
        });
        component.open();
        expect(modalServiceOpenStub).to.have.been.called;
    });

    it('should invoke updatePosting when confirming', () => {
        component.posting = metisAnswerPostUser1;
        createPostingSpy = spy(component, 'updatePosting');
        component.ngOnInit();
        component.confirm();
        expect(createPostingSpy).to.be.have.been.called;
    });

    it('should invoke createPosting when confirming without posting id', () => {
        component.posting = metisAnswerPostUser1;
        createPostingSpy = spy(component, 'updatePosting');
        component.ngOnInit();
        component.confirm();
        expect(createPostingSpy).to.be.have.been.called;
    });

    it('should invoke metis service with created answer post', fakeAsync(() => {
        const metisServiceCreateSpy = spy(metisService, 'createAnswerPost');
        const onCreateSpy = spy(component.onCreate, 'emit');
        component.posting = metisAnswerPostToCreateUser1;
        createPostingSpy = spy(component, 'updatePosting');
        component.ngOnInit();
        const newContent = 'New Content';
        component.formGroup.setValue({
            content: newContent,
        });
        component.confirm();
        expect(metisServiceCreateSpy).to.be.have.been.calledWith({ ...component.posting, content: newContent });
        tick();
        expect(component.isLoading).to.equal(false);
        expect(onCreateSpy).to.have.been.called;
    }));

    it('should invoke metis service with updated answer post', fakeAsync(() => {
        const metisServiceCreateSpy = spy(metisService, 'updateAnswerPost');
        component.posting = metisAnswerPostUser2;
        createPostingSpy = spy(component, 'updatePosting');
        component.ngOnInit();
        const updatedContent = 'Updated Content';
        component.formGroup.setValue({
            content: updatedContent,
        });
        component.confirm();
        expect(metisServiceCreateSpy).to.be.have.been.calledWith({ ...component.posting, content: updatedContent });
        tick();
        expect(component.isLoading).to.equal(false);
    }));

    it('should update content when posting content changed', () => {
        component.posting = metisAnswerPostUser2;
        component.posting.content = 'New content';
        component.ngOnChanges();
        expect(component.content).to.be.equal(component.posting.content);
    });
});
