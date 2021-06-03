import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ActivatedRoute } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { PostingsComponent } from 'app/overview/postings/postings.component';
import { Lecture } from 'app/entities/lecture.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';
import { Course } from 'app/entities/course.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('PostRowComponent', () => {
    let component: PostingsComponent;
    let componentFixture: ComponentFixture<PostingsComponent>;

    const course = {
        id: 1,
    } as Course;

    const unApprovedAnswerPost = {
        id: 1,
        creationDate: undefined,
        content: 'not approved',
        tutorApproved: false,
    } as AnswerPost;

    const approvedAnswerPost = {
        id: 2,
        creationDate: undefined,
        content: 'approved',
        tutorApproved: true,
    } as AnswerPost;

    const post1 = {
        id: 1,
        creationDate: undefined,
        answers: [unApprovedAnswerPost, approvedAnswerPost],
    } as Post;

    const post2 = {
        id: 2,
        creationDate: undefined,
        answers: [unApprovedAnswerPost, approvedAnswerPost],
    } as Post;

    const lectureDefault = {
        id: 1,
        title: 'test',
        description: 'test',
        startDate: undefined,
        endDate: undefined,
        posts: [post1, post2],
        isAtLeastInstructor: true,
        course,
    } as Lecture;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule],
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects },
            ],
            declarations: [PostingsComponent],
        })
            .overrideTemplate(PostingsComponent, '')
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(PostingsComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should set student posts correctly', () => {
        component.lecture = lectureDefault;
        component.ngOnInit();
        expect(component.posts).to.deep.equal([post1, post2]);
    });

    it('should delete post from list', () => {
        component.lecture = lectureDefault;
        component.ngOnInit();
        component.deletePostFromList(post1);
        expect(component.posts).to.deep.equal([post2]);
    });
});
