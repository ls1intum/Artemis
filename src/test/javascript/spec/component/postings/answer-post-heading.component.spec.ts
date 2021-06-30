import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ActivatedRoute } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { AnswerPostComponent } from 'app/shared/metis/answer-post/answer-post.component';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';
import { PostingsButtonComponent } from 'app/shared/metis/postings-button/postings-button.component';
import { MockDirective } from 'ng-mocks';
import { PostingsMarkdownEditorComponent } from 'app/shared/metis/postings-markdown-editor/postings-markdown-editor.component';
import { AnswerPostHeaderComponent } from 'app/shared/metis/answer-post/answer-post-header/answer-post-header.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('AnswerPostHeaderComponent', () => {
    let component: AnswerPostHeaderComponent;
    let componentFixture: ComponentFixture<AnswerPostHeaderComponent>;

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
            declarations: [AnswerPostComponent, AnswerPostHeaderComponent, MockDirective(PostingsButtonComponent), MockDirective(PostingsMarkdownEditorComponent)],
            providers: [{ provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects }],
        })
            .overrideTemplate(AnswerPostComponent, '')
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(AnswerPostHeaderComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should be author of answer', () => {
        component.answerPost = approvedAnswerPost;
        component.user = user2;
        expect(component.isAuthorOfAnswerPost).to.be.true;
    });

    it('should not be author of answer', () => {
        component.answerPost = approvedAnswerPost;
        component.user = user2;
        expect(component.isAuthorOfAnswerPost).to.be.false;
    });
});
