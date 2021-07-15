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
import { AnswerPostHeaderComponent } from 'app/shared/metis/postings-header/answer-post-header/answer-post-header.component';

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
            declarations: [AnswerPostComponent, MockDirective(AnswerPostHeaderComponent), MockDirective(PostingsButtonComponent), MockDirective(PostingsMarkdownEditorComponent)],
            providers: [{ provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects }],
        })
            .overrideTemplate(AnswerPostComponent, '')
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(AnswerPostComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should approve answer', () => {
        component.posting = unApprovedAnswerPost;
        component.toggleAnswerPostTutorApproved();
        expect(component.posting.tutorApproved).to.be.true;
    });

    it('should unapprove answer', () => {
        component.posting = approvedAnswerPost;
        component.toggleAnswerPostTutorApproved();
        expect(component.posting.tutorApproved).to.be.false;
    });

    it('should toggle edit mode and reset editor Text', () => {
        component.posting = approvedAnswerPost;
        component.isEditMode = true;
        component.content = 'test';
        component.toggleEditMode();
        expect(component.content).to.deep.equal('approved');
        expect(component.isEditMode).to.be.false;
        component.toggleEditMode();
        expect(component.isEditMode).to.be.true;
    });

    it('should update answerText', () => {
        component.posting = approvedAnswerPost;
        component.isEditMode = true;
        component.content = 'test';
        component.updatePosting();
        expect(component.posting.content).to.deep.equal('test');
    });
});
