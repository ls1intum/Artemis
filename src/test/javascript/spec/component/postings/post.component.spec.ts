import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ActivatedRoute } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { PostComponent } from 'app/overview/postings/post/post.component';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../test.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { PostVotesComponent } from 'app/overview/postings/post-votes/post-votes.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { PostingsButtonComponent } from 'app/overview/postings/postings-button/postings-button.component';
import { PostingsMarkdownEditorComponent } from 'app/overview/postings/postings-markdown-editor/postings-markdown-editor.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('PostComponent', () => {
    let component: PostComponent;
    let componentFixture: ComponentFixture<PostComponent>;

    const user1 = {
        id: 1,
    } as User;

    const user2 = {
        id: 2,
    } as User;

    const unApprovedPostAnswer = {
        id: 1,
        creationDate: undefined,
        content: 'not approved',
        tutorApproved: false,
        author: user1,
    } as AnswerPost;

    const approvedPostAnswer = {
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
        author: user1,
        answers: [unApprovedPostAnswer, approvedPostAnswer],
    } as Post;

    const maliciousPost = {
        id: 2,
        content: '<div style="transform: scaleX(-1)">&gt;:)</div>',
        creationDate: undefined,
        author: user2,
        answers: [],
    } as Post;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule],
            declarations: [
                PostComponent,
                MockDirective(MarkdownEditorComponent),
                MockDirective(ConfirmIconComponent),
                MockDirective(PostVotesComponent),
                MockDirective(NgbTooltip),
                MockDirective(PostingsButtonComponent),
                MockDirective(PostingsMarkdownEditorComponent),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTranslatePipe),
                // Don't mock this since we want to test this pipe, too
                HtmlForMarkdownPipe,
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: {
                                get: () => {
                                    return { courseId: 1 };
                                },
                            },
                        },
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(PostComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should toggle edit mode and reset editor Text', () => {
        component.post = post;
        component.isEditMode = true;
        component.content = 'test';
        component.toggleEditMode();
        expect(component.content).to.deep.equal('post');
        expect(component.isEditMode).to.be.false;
        component.toggleEditMode();
        expect(component.isEditMode).to.be.true;
    });

    it('should update content', () => {
        component.post = post;
        component.isEditMode = true;
        component.content = 'test';
        component.savePost();
        expect(component.post.content).to.deep.equal('test');
    });

    it('should not display malicious html in post texts', () => {
        component.post = maliciousPost;
        componentFixture.detectChanges();

        const text = componentFixture.debugElement.nativeElement.querySelector('#content');
        expect(text.innerHTML).to.not.equal(maliciousPost.content);
        expect(text.innerHTML).to.equal('&gt;:)');
    });
});
