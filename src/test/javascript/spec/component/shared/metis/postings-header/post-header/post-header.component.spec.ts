import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { DebugElement, NO_ERRORS_SCHEMA } from '@angular/core';
import * as moment from 'moment';
import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { Post } from 'app/entities/metis/post.model';
import { User } from 'app/core/user/user.model';
import { PostHeaderComponent } from 'app/shared/metis/postings-header/post-header/post-header.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { getElement } from '../../../../../helpers/utils/general.utils';
import { PostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { FormBuilder } from '@angular/forms';

chai.use(sinonChai);
const expect = chai.expect;

describe('PostHeaderComponent', () => {
    let component: PostHeaderComponent;
    let fixture: ComponentFixture<PostHeaderComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceUserIsAtLeastTutorStub: SinonStub;
    let metisServiceDeletePostStub: SinonStub;

    const user = { id: 1, name: 'username', login: 'login' } as User;

    const post = {
        id: 1,
        author: user,
        creationDate: moment(),
        answers: [],
        content: 'Post Content',
    } as Post;

    const answerPost1 = {
        id: 1,
        content: 'Some answer',
    } as AnswerPost;

    const answerPost2 = {
        id: 2,
        content: 'Some answer',
    } as AnswerPost;

    const answerPosts: AnswerPost[] = [answerPost1, answerPost2];

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [],
            providers: [FormBuilder, { provide: MetisService, useClass: MockMetisService }],
            declarations: [PostHeaderComponent, PostCreateEditModalComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe)],
            schemas: [NO_ERRORS_SCHEMA],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostHeaderComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceUserIsAtLeastTutorStub = stub(metisService, 'metisUserIsAtLeastTutorInCourse');
                metisServiceDeletePostStub = stub(metisService, 'deletePost');
                debugElement = fixture.debugElement;
                component.posting = post;
                component.ngOnInit();
            });
    });

    afterEach(function () {
        sinon.restore();
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
        component.posting.answers = answerPosts;
        component.ngOnChanges();
        fixture.detectChanges();
        expect(component.numberOfAnswerPosts).to.be.equal(answerPosts.length);
        expect(getElement(debugElement, '.answer-count').innerHTML).contains(answerPosts.length);
        expect(getElement(debugElement, '.answer-count .icon')).to.exist;
        expect(getElement(debugElement, '.toggle-answer-element.clickable')).to.exist;
    });

    it('should call toggleAnswers method and emit event when answer count icon is clicked', () => {
        const toggleAnswersSpy = sinon.spy(component, 'toggleAnswers');
        const toggleAnswersChangeSpy = sinon.spy(component.toggleAnswersChange, 'emit');
        component.posting.answers = answerPosts;
        component.ngOnChanges();
        fixture.detectChanges();
        getElement(debugElement, '.toggle-answer-element.clickable').click();
        expect(toggleAnswersSpy).to.have.been.called;
        expect(toggleAnswersChangeSpy).to.have.been.called;
    });
});
