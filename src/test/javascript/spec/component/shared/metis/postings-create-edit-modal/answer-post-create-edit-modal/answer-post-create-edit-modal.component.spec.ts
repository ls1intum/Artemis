import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import * as sinon from 'sinon';
import { spy } from 'sinon';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { User } from 'app/core/user/user.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { MockNgbModalService } from '../../../../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { PostingsMarkdownEditorComponent } from 'app/shared/metis/postings-markdown-editor/postings-markdown-editor.component';
import { PostingsButtonComponent } from 'app/shared/metis/postings-button/postings-button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('AnswerPostCreateEditModalComponent', () => {
    let component: AnswerPostCreateEditModalComponent;
    let fixture: ComponentFixture<AnswerPostCreateEditModalComponent>;
    let metisService: MetisService;
    let modal: MockNgbModalService;
    let answerPost: AnswerPost;

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

                const user = { id: 1, name: 'username', login: 'login' } as User;

                answerPost = {
                    id: 1,
                    author: user,
                    content: 'Answer Post Content',
                } as AnswerPost;

                component.posting = answerPost;
                component.ngOnInit();
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should init modal with correct content and title for answer post with id', () => {
        expect(component.modalTitle).to.be.equal('artemisApp.metis.editPosting');
        expect(component.content).to.be.equal(answerPost.content);
    });

    it('should init modal with correct content and title for answer post without id', () => {
        component.posting.id = undefined;
        component.posting.content = undefined;
        component.ngOnInit();
        expect(component.modalTitle).to.be.equal('artemisApp.metis.createModalTitleAnswer');
        expect(component.content).to.be.equal('');
    });

    it('should invoke the modalService', () => {
        const componentInstance = { title: string, content: string };
        const result = new Promise((resolve) => resolve(true));
        const modalServiceOpenStub = sinon.stub(modal, 'open').returns(<NgbModalRef>{
            componentInstance,
            result,
        });
        component.open();
        expect(modalServiceOpenStub).to.have.been.called;
    });

    it('should invoke updatePosting when confirming', () => {
        const createPostingSpy = spy(component, 'updatePosting');
        component.confirm();
        expect(createPostingSpy).to.be.have.been.called;
    });

    it('should invoke createPosting when confirming without posting id', () => {
        component.posting.id = undefined;
        const createPostingStub = spy(component, 'createPosting');
        component.confirm();
        expect(createPostingStub).to.be.have.been.called;
    });

    it('should invoke metis service with created answer post', fakeAsync(() => {
        const metisServiceCreateSpy = spy(metisService, 'createAnswerPost');
        const onCreateSpy = spy(component.onCreate, 'emit');
        component.posting.id = undefined;
        const newContent = 'New Content';
        component.formGroup.setValue({
            content: newContent,
        });
        component.confirm();
        expect(metisServiceCreateSpy).to.be.have.been.calledWith({ ...component.posting, content: newContent });
        expect(component.posting.creationDate).to.not.be.undefined;
        tick();
        expect(component.isLoading).to.equal(false);
        expect(onCreateSpy).to.have.been.called;
    }));

    it('should invoke metis service with updated answer post', fakeAsync(() => {
        const metisServiceCreateSpy = spy(metisService, 'updateAnswerPost');
        const updatedContent = 'Updated Content';
        component.formGroup.setValue({
            content: updatedContent,
        });
        component.confirm();
        expect(metisServiceCreateSpy).to.be.have.been.calledWith({ ...component.posting, content: updatedContent });
        tick();
        expect(component.isLoading).to.equal(false);
    }));

    it('should invoke updateModal title on changes', () => {
        component.posting.content = 'New content';
        component.ngOnChanges();
        expect(component.content).to.be.equal(component.posting.content);
    });
});
