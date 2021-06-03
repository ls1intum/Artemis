import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { CoursePostsComponent } from 'app/course/course-posts/course-posts.component';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';
import { PostForOverview } from 'app/course/course-posts/course-posts.component';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

chai.use(sinonChai);
const expect = chai.expect;

describe('CoursePostsComponent', () => {
    let component: CoursePostsComponent;
    let componentFixture: ComponentFixture<CoursePostsComponent>;

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

    const post = {
        id: 1,
        content: 'post',
        creationDate: undefined,
        answers: [unApprovedAnswerPost, approvedAnswerPost],
    } as Post;

    const postForOverview = {
        id: 1,
        content: 'post',
        creationDate: undefined,
        votes: 1,
        answers: 2,
        approvedAnswerPosts: 1,
        exerciseOrLectureId: 1,
        exerciseOrLectureTitle: 'Test exercise',
        belongsToExercise: true,
    } as PostForOverview;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule],
            declarations: [CoursePostsComponent],
        })
            .overrideTemplate(CoursePostsComponent, '')
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CoursePostsComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should count approved answers correctly', () => {
        expect(component.getNumberOfApprovedAnswerPosts(post)).to.deep.equal(1);
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
