import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AnswerPostComponent } from 'app/shared/metis/answer-post/answer-post.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { DebugElement } from '@angular/core';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { getElement } from '../../../../helpers/utils/general.utils';
import { AnswerPostHeaderComponent } from 'app/shared/metis/posting-header/answer-post-header/answer-post-header.component';
import { AnswerPostFooterComponent } from 'app/shared/metis/posting-footer/answer-post-footer/answer-post-footer.component';
import { PostingContentComponent } from 'app/shared/metis/posting-content/posting-content.components';
import { metisResolvingAnswerPostUser1 } from '../../../../helpers/sample/metis-sample-data';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';

describe('AnswerPostComponent', () => {
    let component: AnswerPostComponent;
    let fixture: ComponentFixture<AnswerPostComponent>;
    let debugElement: DebugElement;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [
                AnswerPostComponent,
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(AnswerPostHeaderComponent),
                MockComponent(PostingContentComponent),
                MockComponent(AnswerPostCreateEditModalComponent),
                MockComponent(AnswerPostFooterComponent),
            ],
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
        expect(header).not.toBeNull();
    });

    it('should contain reference to container for rendering answerPostCreateEditModal component', () => {
        expect(component.containerRef).not.toBeNull();
    });

    it('should contain component to edit answer post', () => {
        const answerPostCreateEditModal = getElement(debugElement, 'jhi-answer-post-create-edit-modal');
        expect(answerPostCreateEditModal).not.toBeNull();
    });

    it('should contain an answer post footer', () => {
        const footer = getElement(debugElement, 'jhi-answer-post-footer');
        expect(footer).not.toBeNull();
    });

    it('should have correct content', () => {
        component.posting = metisResolvingAnswerPostUser1;
        component.ngOnInit();
        expect(component.content).toEqual(metisResolvingAnswerPostUser1.content);
    });
});
