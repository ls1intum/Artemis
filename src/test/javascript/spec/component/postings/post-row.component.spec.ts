import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ActivatedRoute } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { PostRowComponent } from 'app/overview/postings/post-row/post-row.component';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';

chai.use(sinonChai);
const expect = chai.expect;

describe('PostRowComponent', () => {
    let component: PostRowComponent;
    let componentFixture: ComponentFixture<PostRowComponent>;

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
        creationDate: undefined,
        answers: [unApprovedAnswerPost, approvedAnswerPost],
    } as Post;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects },
            ],
            declarations: [PostRowComponent],
        })
            .overrideTemplate(PostRowComponent, '')
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(PostRowComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should sort in approved and not approved answers', () => {
        component.post = post;
        component.sortAnswerPosts();
        expect(component.approvedAnswerPosts).to.deep.equal([approvedAnswerPost]);
        expect(component.sortedAnswerPosts).to.deep.equal([unApprovedAnswerPost]);
    });

    it('should delete answerPost from list', () => {
        component.post = post;
        component.sortAnswerPosts();
        component.deleteAnswerFromList(unApprovedAnswerPost);
        expect(component.post.answers).to.deep.equal([approvedAnswerPost]);
    });

    it('should add answerPost to list', () => {
        component.post = post;
        component.sortAnswerPosts();
        component.post.answers = [approvedAnswerPost];
        component.addAnswerPostToList(unApprovedAnswerPost);
        expect(component.post.answers).to.deep.equal([approvedAnswerPost, unApprovedAnswerPost]);
    });
});
