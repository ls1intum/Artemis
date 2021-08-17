import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';
import { PostingsThreadComponent } from 'app/shared/metis/postings-thread/postings-thread.component';
import { PostService } from 'app/shared/metis/post.service';
import { MockPostService } from '../../../../helpers/mocks/service/mock-post.service';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { MockAnswerPostService } from '../../../../helpers/mocks/service/mock-answer-post.service';
import { MetisService } from 'app/shared/metis/metis.service';
import * as moment from 'moment';
import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { AnswerPostComponent } from 'app/shared/metis/answer-post/answer-post.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('PostingsThreadComponent', () => {
    let component: PostingsThreadComponent;
    let fixture: ComponentFixture<PostingsThreadComponent>;
    let metisService: MetisService;
    let metisServiceUserAuthorityStub: SinonStub;

    const unApprovedAnswerPost1 = {
        id: 1,
        creationDate: moment(),
        content: 'not approved most recent',
        tutorApproved: false,
    } as AnswerPost;

    const unApprovedAnswerPost2 = {
        id: 2,
        creationDate: moment().subtract(1, 'day'),
        content: 'not approved',
        tutorApproved: false,
    } as AnswerPost;

    const approvedAnswerPost = {
        id: 2,
        creationDate: undefined,
        content: 'approved',
        tutorApproved: true,
    } as AnswerPost;

    const sortedAnswerArray: AnswerPost[] = [approvedAnswerPost, unApprovedAnswerPost2, unApprovedAnswerPost1];
    const unsortedAnswerArray: AnswerPost[] = [unApprovedAnswerPost1, unApprovedAnswerPost2, approvedAnswerPost];

    const post = {
        id: 1,
        creationDate: undefined,
        answers: unsortedAnswerArray,
    } as Post;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [],
            providers: [
                { provide: PostService, useClass: MockPostService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: MetisService, useClass: MockMetisService },
            ],
            declarations: [
                PostingsThreadComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(PostComponent),
                MockComponent(AnswerPostComponent),
                MockComponent(FaIconComponent),
                MockComponent(AnswerPostCreateEditModalComponent),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostingsThreadComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceUserAuthorityStub = stub(metisService, 'metisUserIsAtLeastTutorInCourse');
            });
    });

    afterEach(function () {
        sinon.restore();
    });
    it('should be initialized correctly for users that are at least tutors in course', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        metisServiceUserAuthorityStub.returns(true);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).to.deep.equal(true);
        expect(component.createdAnswerPost.tutorApproved).to.be.equal(true);
    });

    it('should be initialized correctly for users that are not at least tutors in course', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        metisServiceUserAuthorityStub.returns(false);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).to.deep.equal(false);
        expect(component.createdAnswerPost.tutorApproved).to.be.equal(false);
    });

    it('should sort answers', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.sortAnswerPosts();
        expect(component.sortedAnswerPosts).to.deep.equal(sortedAnswerArray);
    });

    it('should not sort empty array of answers', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.post.answers = undefined;
        component.sortAnswerPosts();
        expect(component.sortedAnswerPosts).to.deep.equal([]);
    });

    it('should sort answers on changes', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.ngOnChanges();
        expect(component.sortedAnswerPosts).to.deep.equal(sortedAnswerArray);
    });

    it('should toggle answers', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.showAnswers = false;
        component.toggleAnswers();
        expect(component.showAnswers).to.be.equal(true);
    });

    it('answer now button should not be visible if post does not yet have any answers', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.nativeElement.querySelector('button');
        expect(answerNowButton).to.not.exist;
    });

    it('answer now button should be visible if post does not yet have any answers', () => {
        component.post = post;
        component.post.answers = [];
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.nativeElement.querySelector('button');
        expect(answerNowButton).to.exist;
    });

    it('should contain a post', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.ngOnInit();
        const postComponent = fixture.debugElement.nativeElement.querySelector('jhi-post');
        expect(postComponent).to.exist;
    });

    it('should contain an answer post', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.showAnswers = true;
        fixture.detectChanges();
        const answerPostComponent = fixture.debugElement.nativeElement.querySelector('jhi-answer-post');
        expect(answerPostComponent).to.exist;
    });

    it('should not contain an answer post', () => {
        component.post = post;
        component.post.answers = unsortedAnswerArray;
        component.showAnswers = false;
        fixture.detectChanges();
        const answerPostComponent = fixture.debugElement.nativeElement.querySelector('jhi-answer-post');
        expect(answerPostComponent).to.not.exist;
    });
});
