import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CoursePostsComponent, PostForOverview } from 'app/course/course-posts/course-posts.component';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../../test.module';
import { PostService } from 'app/shared/metis/post.service';
import { MockPostService } from '../../../helpers/mocks/service/mock-post.service';
import { spy } from 'sinon';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

chai.use(sinonChai);
const expect = chai.expect;

describe('CoursePostsComponent', () => {
    let component: CoursePostsComponent;
    let fixture: ComponentFixture<CoursePostsComponent>;
    let postService: PostService;

    const user1 = {
        id: 1,
    } as User;

    const user2 = {
        id: 2,
    } as User;

    const unApprovedAnswerPost = {
        id: 1,
        creationDate: undefined,
        content: 'not approved',
        tutorApproved: false,
        author: user1,
    } as AnswerPost;

    const approvedAnswerPost = {
        id: 2,
        creationDate: undefined,
        content: 'approved',
        tutorApproved: true,
        author: user2,
    } as AnswerPost;

    const post1 = {
        id: 1,
        content: 'post',
        creationDate: undefined,
        answers: [unApprovedAnswerPost, approvedAnswerPost],
    } as Post;

    const postForOverview = {
        id: 1,
        content: 'post',
        creationDate: undefined,
        answers: 2,
        votes: 1,
        approvedAnswerPosts: 1,
        exerciseOrLectureId: 1,
        exerciseOrLectureTitle: 'Test exercise',
        belongsToExercise: true,
    } as PostForOverview;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, ArtemisTestModule],
            providers: [{ provide: PostService, useClass: MockPostService }],
            declarations: [CoursePostsComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockPipe(HtmlForMarkdownPipe)],
            schemas: [NO_ERRORS_SCHEMA],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CoursePostsComponent);
                component = fixture.componentInstance;
                component.courseId = 1;
                postService = TestBed.inject(PostService);
            });
    });

    it('should initialize post for overview correctly', () => {
        const postServiceGetPostByCourseIdSpy = spy(postService, 'getAllPostsByCourseId');
        component.updatePosts();
        expect(postServiceGetPostByCourseIdSpy).to.have.been.called;
    });

    it('should count approved answers correctly', () => {
        expect(component.getNumberOfApprovedAnswerPosts(post1)).to.deep.equal(1);
    });

    it('should hide questions with approved answers', () => {
        component.posts = [postForOverview];
        component.hidePostsWithApprovedAnswerPosts();
        expect(component.postsToDisplay.length).to.deep.equal(0);
    });

    it('should toggle hiding questions with approved answers', () => {
        component.posts = [postForOverview];
        expect(component.showPostsWithApprovedAnswerPosts).to.be.false;
        component.toggleHidePosts();
        expect(component.showPostsWithApprovedAnswerPosts).to.be.true;
        expect(component.postsToDisplay.length).to.deep.equal(1);
        component.toggleHidePosts();
        expect(component.showPostsWithApprovedAnswerPosts).to.be.false;
        expect(component.postsToDisplay.length).to.deep.equal(0);
    });
});
