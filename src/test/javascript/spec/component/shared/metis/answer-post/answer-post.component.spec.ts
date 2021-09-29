import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AnswerPostComponent } from 'app/shared/metis/answer-post/answer-post.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { DebugElement } from '@angular/core';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { getElement } from '../../../../helpers/utils/general.utils';
import { AnswerPostHeaderComponent } from 'app/shared/metis/postings-header/answer-post-header/answer-post-header.component';
import { AnswerPostFooterComponent } from 'app/shared/metis/postings-footer/answer-post-footer/answer-post-footer.component';
import { PostingContentComponent } from 'app/shared/metis/posting-content/posting-content.components';
import { metisAnswerPostUser1 } from '../../../../helpers/sample/metis-sample-data';
import { PostingContentComponent } from 'app/shared/metis/posting-content/posting-content.components';

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
        expect(header).toBeDefined();
    });

    it('should contain an answer post footer', () => {
        const footer = getElement(debugElement, 'jhi-answer-post-footer');
        expect(footer).toBeDefined();
    });

    it('should have correct content', () => {
        component.posting = metisAnswerPostUser1;
        component.ngOnInit();
        expect(component.content).toEqual(metisAnswerPostUser1.content);
    });
});
