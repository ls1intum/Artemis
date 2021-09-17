import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { DebugElement } from '@angular/core';
import * as moment from 'moment';
import { Moment } from 'moment';
import * as sinon from 'sinon';
import { SinonStub, spy, stub } from 'sinon';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { getElement } from '../../../../../helpers/utils/general.utils';
import { AnswerPostHeaderComponent } from 'app/shared/metis/postings-header/answer-post-header/answer-post-header.component';
import { MockNgbModalService } from '../../../../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { PostingsMarkdownEditorComponent } from 'app/shared/metis/postings-markdown-editor/postings-markdown-editor.component';
import { PostingsButtonComponent } from 'app/shared/metis/postings-button/postings-button.component';
import { metisAnswerPostUser1, metisUser1 } from '../../../../../helpers/sample/metis-sample-data';

chai.use(sinonChai);
const expect = chai.expect;

describe('AnswerPostHeaderComponent', () => {
    let component: AnswerPostHeaderComponent;
    let fixture: ComponentFixture<AnswerPostHeaderComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceUserIsAtLeastTutorStub: SinonStub;
    let metisServiceUserPostAuthorStub: SinonStub;
    let metisServiceDeletePostStub: SinonStub;
    let modal: MockNgbModalService;

    const yesterday: Moment = moment().subtract(1, 'day');

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(FormsModule), MockModule(ReactiveFormsModule)],
            providers: [
                FormBuilder,
                { provide: MetisService, useClass: MockMetisService },
                {
                    provide: NgbModal,
                    useClass: MockNgbModalService,
                },
            ],
            declarations: [
                AnswerPostHeaderComponent,
                AnswerPostCreateEditModalComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(NgbTooltip),
                MockComponent(PostingsMarkdownEditorComponent),
                MockComponent(PostingsButtonComponent),
                MockComponent(FaIconComponent),
                MockComponent(ConfirmIconComponent),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AnswerPostHeaderComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                modal = TestBed.inject(NgbModal);
                metisServiceUserIsAtLeastTutorStub = stub(metisService, 'metisUserIsAtLeastTutorInCourse');
                metisServiceUserPostAuthorStub = stub(metisService, 'metisUserIsAuthorOfPosting');
                metisServiceDeletePostStub = stub(metisService, 'deleteAnswerPost');
                debugElement = fixture.debugElement;
                component.posting = metisAnswerPostUser1;
                component.posting.creationDate = yesterday;
                component.ngOnInit();
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should set author information correctly', () => {
        fixture.detectChanges();
        const headerAuthorAndDate = getElement(debugElement, '.posting-header.header-author-date');
        expect(headerAuthorAndDate).to.exist;
        expect(headerAuthorAndDate.innerHTML).to.contain(metisUser1.name);
    });

    it('should set date information correctly for post of yesterday', () => {
        component.posting.creationDate = yesterday;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.today-flag')).to.not.exist;
    });

    it('should display edit and delete options to post author', () => {
        metisServiceUserPostAuthorStub.returns(true);
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).to.exist;
        expect(getElement(debugElement, '.deleteIcon')).to.exist;
    });

    it('should not display edit and delete options to users that are neither author or tutor', () => {
        metisServiceUserIsAtLeastTutorStub.returns(false);
        metisServiceUserPostAuthorStub.returns(false);
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).to.not.exist;
        expect(getElement(debugElement, '.deleteIcon')).to.not.exist;
    });

    it('should open modal when edit icon is clicked', () => {
        metisServiceUserPostAuthorStub.returns(true);
        const modalSpy = spy(modal, 'open');
        fixture.detectChanges();
        getElement(debugElement, '.editIcon').click();
        expect(modalSpy).to.have.been.called;
    });

    it('should invoke metis service when delete icon is clicked', () => {
        metisServiceUserIsAtLeastTutorStub.returns(true);
        fixture.detectChanges();
        expect(getElement(debugElement, '.deleteIcon')).to.exist;
        component.deletePosting();
        expect(metisServiceDeletePostStub).to.have.been.called;
    });
});
