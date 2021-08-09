import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AnswerPostComponent } from 'app/shared/metis/answer-post/answer-post.component';
import { MockPipe } from 'ng-mocks';
import { DebugElement, NO_ERRORS_SCHEMA } from '@angular/core';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { getElement } from '../../../../helpers/utils/general.utils';

chai.use(sinonChai);
const expect = chai.expect;

describe('AnswerPostComponent', () => {
    let component: AnswerPostComponent;
    let fixture: ComponentFixture<AnswerPostComponent>;
    let debugElement: DebugElement;

    const answerPost = {
        id: 2,
        creationDate: undefined,
        content: 'content',
        tutorApproved: false,
    } as AnswerPost;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [],
            declarations: [AnswerPostComponent, MockPipe(HtmlForMarkdownPipe)],
            schemas: [NO_ERRORS_SCHEMA],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AnswerPostComponent);
                component = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    it('should contain an answer post header', () => {
        const header = getElement(debugElement, 'jhi-answer-post-header');
        expect(header).to.exist;
    });

    it('should contain an answer post footer', () => {
        const footer = getElement(debugElement, 'jhi-answer-post-footer');
        expect(footer).to.exist;
    });

    it('should have correct content', () => {
        component.posting = answerPost;
        component.ngOnInit();
        expect(component.content).to.be.equal(answerPost.content);
    });
});
