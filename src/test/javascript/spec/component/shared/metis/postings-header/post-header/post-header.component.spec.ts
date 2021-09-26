import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { DebugElement } from '@angular/core';
import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { PostHeaderComponent } from 'app/shared/metis/postings-header/post-header/post-header.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { getElement } from '../../../../../helpers/utils/general.utils';
import { PostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostingsMarkdownEditorComponent } from 'app/shared/metis/postings-markdown-editor/postings-markdown-editor.component';
import { PostingsButtonComponent } from 'app/shared/metis/postings-button/postings-button.component';
import { metisAnswerPosts, metisPostLectureUser1 } from '../../../../../helpers/sample/metis-sample-data';

chai.use(sinonChai);
const expect = chai.expect;

describe('PostHeaderComponent', () => {
    let component: PostHeaderComponent;
    let fixture: ComponentFixture<PostHeaderComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceUserIsAtLeastTutorStub: SinonStub;
    let metisServiceDeletePostStub: SinonStub;
    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(FormsModule), MockModule(ReactiveFormsModule)],
            providers: [FormBuilder, { provide: MetisService, useClass: MockMetisService }],
            declarations: [
                PostHeaderComponent,
                MockComponent(PostCreateEditModalComponent),
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
                fixture = TestBed.createComponent(PostHeaderComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceUserIsAtLeastTutorStub = stub(metisService, 'metisUserIsAtLeastTutorInCourse');
                metisServiceDeletePostStub = stub(metisService, 'deletePost');
                debugElement = fixture.debugElement;
                component.posting = metisPostLectureUser1;
                component.ngOnInit();
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should have information on answers after init', () => {
        expect(component.numberOfAnswerPosts).to.not.be.undefined;
        expect(component.hasApprovedAnswers).to.not.be.undefined;
    });

    it('should set date information correctly for post of today', () => {
        fixture.detectChanges();
        expect(getElement(debugElement, '.today-flag')).to.exist;
    });

    it('should display edit and delete options to tutor', () => {
        metisServiceUserIsAtLeastTutorStub.returns(true);
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).to.exist;
        expect(getElement(debugElement, '.deleteIcon')).to.exist;
    });

    it('should invoke metis service when delete icon is clicked', () => {
        metisServiceUserIsAtLeastTutorStub.returns(true);
        fixture.detectChanges();
        expect(getElement(debugElement, '.deleteIcon')).to.exist;
        component.deletePosting();
        expect(metisServiceDeletePostStub).to.have.been.called;
    });

    it('should only display non clickable answer-count icon for post without answers', () => {
        component.ngOnChanges();
        fixture.detectChanges();
        expect(component.numberOfAnswerPosts).to.be.equal(0);
        expect(getElement(debugElement, '.answer-count').innerHTML).contains(0);
        expect(getElement(debugElement, '.answer-count .icon')).to.exist;
        expect(getElement(debugElement, '.toggle-answer-element.clickable')).to.not.exist;
    });

    it('should only display non clickable icon for post with answers', () => {
        component.posting.answers = metisAnswerPosts;
        component.ngOnChanges();
        fixture.detectChanges();
        expect(component.numberOfAnswerPosts).to.be.equal(metisAnswerPosts.length);
        expect(getElement(debugElement, '.answer-count').innerHTML).contains(metisAnswerPosts.length);
        expect(getElement(debugElement, '.answer-count .icon')).to.exist;
        expect(getElement(debugElement, '.toggle-answer-element.clickable')).to.exist;
    });

    it('should call toggleAnswers method and emit event when answer count icon is clicked', () => {
        const toggleAnswersSpy = sinon.spy(component, 'toggleAnswers');
        const toggleAnswersChangeSpy = sinon.spy(component.toggleAnswersChange, 'emit');
        component.posting.answers = metisAnswerPosts;
        component.ngOnChanges();
        fixture.detectChanges();
        getElement(debugElement, '.toggle-answer-element.clickable').click();
        expect(toggleAnswersSpy).to.have.been.called;
        expect(toggleAnswersChangeSpy).to.have.been.called;
    });
});
