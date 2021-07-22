import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { DebugElement, NO_ERRORS_SCHEMA } from '@angular/core';
import * as moment from 'moment';
import { Moment } from 'moment';
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
import { MockNgbModalService } from '../../../../../helpers/mocks/service/mock-ngb-modal.service';

chai.use(sinonChai);
const expect = chai.expect;

let metisService: MetisService;
let modalService: MockNgbModalService;
let metisServiceUserIsAtLeastTutorStub: SinonStub;
let metisServiceUserPostAuthorStub: SinonStub;
let metisServiceDeletePostStub: SinonStub;
let modalServiceOpenStub: SinonStub;

describe('PostHeaderComponent', () => {
    let component: PostHeaderComponent;
    let fixture: ComponentFixture<PostHeaderComponent>;
    let debugElement: DebugElement;

    const user = { id: 1, name: 'usersame', login: 'login' } as User;

    const today: Moment = moment();
    const yesterday: Moment = moment().subtract(1, 'day');

    const post = {
        id: 1,
        author: user,
        creationDate: yesterday,
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

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [],
            providers: [
                { provide: MetisService, useClass: MockMetisService },
                {
                    provide: MockNgbModalService,
                    useClass: MockNgbModalService,
                },
            ],
            declarations: [PostHeaderComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe)],
            schemas: [NO_ERRORS_SCHEMA],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostHeaderComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                modalService = TestBed.inject(MockNgbModalService);
                metisServiceUserIsAtLeastTutorStub = stub(metisService, 'metisUserIsAtLeastTutorInCourse');
                metisServiceUserPostAuthorStub = stub(metisService, 'metisUserIsAuthorOfPosting');
                metisServiceDeletePostStub = stub(metisService, 'deletePost');
                modalServiceOpenStub = stub(modalService, 'open');
                debugElement = fixture.debugElement;
                component.posting = post;
                component.ngOnInit();
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should set author information correctly', () => {
        fixture.detectChanges();
        const headerAuthorAndDate = fixture.debugElement.nativeElement.querySelector('.posting-header.header-author-date');
        expect(headerAuthorAndDate).to.exist;
        expect(headerAuthorAndDate.innerHTML).to.contain(user.name);
    });

    it('should set date information correctly for post of today', () => {
        component.posting.creationDate = today;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.posting-header.header-author-date').innerHTML).to.contain('Today');
    });

    it('should set date information correctly for post of yesterday', () => {
        component.posting.creationDate = yesterday;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.posting-header.header-author-date').innerHTML).to.not.contain('Today');
    });

    it('should display edit and delete options to tutor', () => {
        metisServiceUserIsAtLeastTutorStub.returns(true);
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).to.exist;
        expect(getElement(debugElement, '.deleteIcon')).to.exist;
    });

    it('should display edit and delete options to post author', () => {
        metisServiceUserPostAuthorStub.returns(true);
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).to.exist;
        expect(getElement(debugElement, '.deleteIcon')).to.exist;
    });

    it('should not display edit and delete options to users that are neither author or tutor', () => {
        metisServiceUserIsAtLeastTutorStub.returns(false);
        metisServiceUserPostAuthorStub.returns(false);
        component.ngOnInit();
        fixture.detectChanges();
        expect(getElement(debugElement, '.editIcon')).to.not.exist;
        expect(getElement(debugElement, '.deleteIcon')).to.not.exist;
    });

    it('should trigger edit modal when icon clicked', () => {
        metisServiceUserIsAtLeastTutorStub.returns(true);
        fixture.detectChanges();
        const clickableEditIcon = fixture.debugElement.nativeElement.querySelector('.editIcon');
        clickableEditIcon.click();
        expect(modalServiceOpenStub).to.have.been.called;
    });

    it('should invoke metis service when delete icon is clicked', () => {
        metisServiceUserIsAtLeastTutorStub.returns(true);
        fixture.detectChanges();
        expect(getElement(debugElement, '.deleteIcon')).to.exist;
        component.deletePosting();
        expect(metisServiceDeletePostStub).to.have.been.called;
    });

    it('should only display non clickable icon for post without answers', () => {
        component.ngOnChanges();
        fixture.detectChanges();
        expect(component.numberOfAnswerPosts).to.be.equal(0);
        expect(getElement(debugElement, '.posting-header.answer-count').innerHTML).contains(0);
        expect(getElement(debugElement, '.answer-count .icon')).to.exist;
        expect(getElement(debugElement, '.toggleAnswerElement.clickable')).to.not.exist;
    });

    it('should only display non clickable icon for post with answers', () => {
        component.posting.answers = answerPosts;
        component.ngOnChanges();
        fixture.detectChanges();
        expect(component.numberOfAnswerPosts).to.be.equal(answerPosts.length);
        expect(getElement(debugElement, '.posting-header.answer-count').innerHTML).contains(answerPosts.length);
        expect(getElement(debugElement, '.answer-count .icon')).to.exist;
        expect(getElement(debugElement, '.toggleAnswerElement.clickable')).to.exist;
    });

    it('should call toggleAnswers method and emit event when answer count icon is clicked', () => {
        const toggleAnswersSpy = sinon.spy(component, 'toggleAnswers');
        const toggleAnswersChangeSpy = sinon.spy(component.toggleAnswersChange, 'emit');
        component.posting.answers = answerPosts;
        component.ngOnChanges();
        fixture.detectChanges();
        getElement(debugElement, '.toggleAnswerElement.clickable').click();
        expect(toggleAnswersSpy).to.have.been.called;
        expect(toggleAnswersChangeSpy).to.have.been.called;
    });
});
