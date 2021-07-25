import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { DebugElement, NO_ERRORS_SCHEMA } from '@angular/core';
import * as sinon from 'sinon';
import { SinonStub, spy, stub } from 'sinon';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { User } from 'app/core/user/user.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { MockNgbModalService } from '../../../../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { of, throwError } from 'rxjs';

chai.use(sinonChai);
const expect = chai.expect;

let metisService: MetisService;
let modal: MockNgbModalService;

describe('AnswerPostCreateEditModalComponent', () => {
    let component: AnswerPostCreateEditModalComponent;
    let fixture: ComponentFixture<AnswerPostCreateEditModalComponent>;

    const user = { id: 1, name: 'usersame', login: 'login' } as User;

    const answerPost = {
        id: 1,
        author: user,
        content: 'Answer Post Content',
    } as AnswerPost;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [],
            providers: [NgbActiveModal, { provide: MetisService, useClass: MockMetisService }, { provide: NgbModal, useClass: MockNgbModalService }],
            declarations: [AnswerPostCreateEditModalComponent, MockPipe(ArtemisTranslatePipe)],
            schemas: [NO_ERRORS_SCHEMA],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AnswerPostCreateEditModalComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                modal = TestBed.inject(NgbModal);
                component.posting = answerPost;
                component.ngOnInit();
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should init modal with correct content and title for answer post with id', () => {
        component.ngOnInit();
        component.updateModalTitle();
        fixture.detectChanges();
        expect(component.modalTitle).to.be.equal('artemisApp.metis.editPosting');
        expect(component.content).to.be.equal('Answer Post Content');
    });

    it('should init modal with correct content and title for answer post without id', () => {
        component.posting.id = undefined;
        component.posting.content = undefined;
        component.ngOnInit();
        component.updateModalTitle();
        fixture.detectChanges();
        expect(component.modalTitle).to.be.equal('artemisApp.metis.createModalTitleAnswer');
        expect(component.content).to.be.equal('');
    });

    it('should invoke the modalService', () => {
        const componentInstance = { title: String, text: String };
        const result = new Promise((resolve) => resolve(true));
        const modalServiceOpenStub = sinon.stub(modal, 'open').returns(<NgbModalRef>{
            componentInstance,
            result,
        });
        component.ngOnInit();
        component.open();
        fixture.detectChanges();
        expect(modalServiceOpenStub).to.have.been.called;
        modalServiceOpenStub.restore();
    });

    it('should invoke updatePosting when confirming save button', () => {
        const createPostingSpy = spy(component, 'updatePosting');
        component.confirm();
        fixture.detectChanges();
        expect(createPostingSpy).to.be.have.been.called;
        createPostingSpy.restore();
    });

    it('should invoke createPosting when confirming save button for posting without id', () => {
        component.posting.id = undefined;
        component.ngOnChanges();
        fixture.detectChanges();
        const updatePostingStub = spy(component, 'createPosting');
        component.confirm();
        fixture.detectChanges();
        expect(updatePostingStub).to.be.have.been.called;
    });

    it('should invoke metis service, set a creation date, and set the loading flag to false when successfully created an answer post', () => {
        component.open();
        fixture.detectChanges();
        const metisServiceCreateStub = stub(metisService, 'createAnswerPost').returns(of(answerPost));
        const onCreateSpy = spy(component.onCreate, 'emit');
        expect(component.modalRef).to.not.be.undefined;
        component.createPosting();
        fixture.detectChanges();
        expect(metisServiceCreateStub).to.be.have.been.called;
        expect(component.isLoading).to.equal(false);
        expect(component.posting.creationDate).to.not.be.undefined;
        expect(onCreateSpy).to.have.been.called;
    });

    it('should invoke metis service, and set loading flag to false when creating an answer post failed', () => {
        component.open();
        fixture.detectChanges();
        const metisServiceCreateStub = stub(metisService, 'createAnswerPost').returns(throwError('ERROR'));
        const onCreateSpy = spy(component.onCreate, 'emit');
        component.createPosting();
        fixture.detectChanges();
        expect(metisServiceCreateStub).to.be.have.been.called;
        expect(onCreateSpy).to.not.have.been.called;
    });

    it('should invoke metis service, set a creation date, and set the loading flag to false when successfully updated an answer post', () => {
        component.open();
        fixture.detectChanges();
        const metisServiceUpdateStub = stub(metisService, 'updateAnswerPost').returns(of(answerPost));
        expect(component.modalRef).to.not.be.undefined;
        component.updatePosting();
        fixture.detectChanges();
        expect(metisServiceUpdateStub).to.be.have.been.called;
        expect(component.isLoading).to.equal(false);
        expect(component.posting.creationDate).to.not.be.undefined;
    });

    it('should invoke metis service, and set loading flag to false when updating an answer post failed', () => {
        component.open();
        fixture.detectChanges();
        const metisServiceUpdateStub = stub(metisService, 'updateAnswerPost').returns(throwError('ERROR'));
        component.updatePosting();
        fixture.detectChanges();
        expect(metisServiceUpdateStub).to.be.have.been.called;
    });

    it('should invoke updateModal title on changes', () => {
        const updateModalTitleStub = spy(component, 'updateModalTitle');
        component.posting.content = 'New content';
        component.ngOnChanges();
        expect(component.content).to.be.equal('New content');
        expect(updateModalTitleStub).to.be.have.been.called;
    });
});
