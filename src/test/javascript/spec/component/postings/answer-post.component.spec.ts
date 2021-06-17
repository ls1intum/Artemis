import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ActivatedRoute } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { AnswerPostComponent } from 'app/overview/postings/answer-post/answer-post.component';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';
import { PostingsButtonComponent } from 'app/overview/postings/postings-button/postings-button.component';
import { MockDirective } from 'ng-mocks';

chai.use(sinonChai);
const expect = chai.expect;

describe('AnswerPostComponent', () => {
    let component: AnswerPostComponent;
    let componentFixture: ComponentFixture<AnswerPostComponent>;

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

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule],
            declarations: [AnswerPostComponent, MockDirective(PostingsButtonComponent)],
            providers: [{ provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects }],
        })
            .overrideTemplate(AnswerPostComponent, '')
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(AnswerPostComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should be author of answer', () => {
        component.answerPost = approvedAnswerPost;
        component.user = user2;
        expect(component.isAuthorOfAnswerPost(approvedAnswerPost)).to.be.true;
    });

    it('should not be author of answer', () => {
        component.answerPost = approvedAnswerPost;
        component.user = user2;
        expect(component.isAuthorOfAnswerPost(unApprovedAnswerPost)).to.be.false;
    });

    it('should approve answer', () => {
        component.answerPost = unApprovedAnswerPost;
        component.toggleAnswerPostTutorApproved();
        expect(component.answerPost.tutorApproved).to.be.true;
    });

    it('should unapprove answer', () => {
        component.answerPost = approvedAnswerPost;
        component.toggleAnswerPostTutorApproved();
        expect(component.answerPost.tutorApproved).to.be.false;
    });

    it('should toggle edit mode and reset editor Text', () => {
        component.answerPost = approvedAnswerPost;
        component.isEditMode = true;
        component.editText = 'test';
        component.toggleEditMode();
        expect(component.editText).to.deep.equal('approved');
        expect(component.isEditMode).to.be.false;
        component.toggleEditMode();
        expect(component.isEditMode).to.be.true;
    });

    it('should update answerText', () => {
        component.answerPost = approvedAnswerPost;
        component.isEditMode = true;
        component.editText = 'test';
        component.saveAnswerPost();
        expect(component.answerPost.content).to.deep.equal('test');
    });
});
