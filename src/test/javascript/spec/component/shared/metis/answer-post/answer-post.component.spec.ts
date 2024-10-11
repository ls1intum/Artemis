import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AnswerPostComponent } from 'app/shared/metis/answer-post/answer-post.component';
import { MockComponent, MockPipe, ngMocks } from 'ng-mocks';
import { DebugElement } from '@angular/core';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { By } from '@angular/platform-browser';
import { AnswerPostHeaderComponent } from 'app/shared/metis/posting-header/answer-post-header/answer-post-header.component';
import { AnswerPostReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/answer-post-reactions-bar/answer-post-reactions-bar.component';
import { PostingContentComponent } from 'app/shared/metis/posting-content/posting-content.components';
import { metisResolvingAnswerPostUser1 } from '../../../../helpers/sample/metis-sample-data';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';

describe('AnswerPostComponent', () => {
    let component: AnswerPostComponent;
    let fixture: ComponentFixture<AnswerPostComponent>;
    let debugElement: DebugElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [
                AnswerPostComponent,
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(AnswerPostHeaderComponent),
                MockComponent(PostingContentComponent),
                MockComponent(AnswerPostCreateEditModalComponent),
                MockComponent(AnswerPostReactionsBarComponent),
            ],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(AnswerPostComponent);
        component = fixture.componentInstance;
        debugElement = fixture.debugElement;
    });

    it('should contain an answer post header when isConsecutive is false', () => {
        component.isConsecutive = false;
        component.posting = metisResolvingAnswerPostUser1;

        fixture.detectChanges();
        const header = debugElement.query(By.css('jhi-answer-post-header'));
        expect(header).not.toBeNull();
    });

    it('should not contain an answer post header when isConsecutive is true', () => {
        component.isConsecutive = true;
        component.posting = metisResolvingAnswerPostUser1;

        fixture.detectChanges();
        const header = debugElement.query(By.css('jhi-answer-post-header'));
        expect(header).toBeNull();
    });

    it('should contain reference to container for rendering answerPostCreateEditModal component', () => {
        component.posting = metisResolvingAnswerPostUser1;

        fixture.detectChanges();
        expect(component.containerRef).not.toBeNull();
    });

    it('should contain component to edit answer post', () => {
        component.posting = metisResolvingAnswerPostUser1;

        fixture.detectChanges();
        const answerPostCreateEditModal = debugElement.query(By.css('jhi-answer-post-create-edit-modal'));
        expect(answerPostCreateEditModal).not.toBeNull();
    });

    it('should contain an answer post reactions bar', () => {
        component.posting = metisResolvingAnswerPostUser1;

        fixture.detectChanges();
        const reactionsBar = debugElement.query(By.css('jhi-answer-post-reactions-bar'));
        expect(reactionsBar).not.toBeNull();
    });

    it('should have correct content in posting-content component', () => {
        component.posting = metisResolvingAnswerPostUser1;

        fixture.detectChanges();
        const postingContentDebugElement = debugElement.query(By.directive(PostingContentComponent));
        expect(postingContentDebugElement).not.toBeNull();
        const content = ngMocks.input(postingContentDebugElement, 'content');
        expect(content).toEqual(metisResolvingAnswerPostUser1.content);
    });
});
