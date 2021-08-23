import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { DebugElement } from '@angular/core';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { Post } from 'app/entities/metis/post.model';
import { getElement } from '../../../../helpers/utils/general.utils';
import { PostFooterComponent } from 'app/shared/metis/postings-footer/post-footer/post-footer.component';
import { PostHeaderComponent } from 'app/shared/metis/postings-header/post-header/post-header.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('PostComponent', () => {
    let component: PostComponent;
    let fixture: ComponentFixture<PostComponent>;
    let debugElement: DebugElement;

    const post = {
        id: 2,
        creationDate: undefined,
        content: 'content',
        title: 'title',
        tags: ['tag'],
    } as Post;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [],
            declarations: [PostComponent, MockPipe(HtmlForMarkdownPipe), MockComponent(PostHeaderComponent), MockComponent(PostFooterComponent)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostComponent);
                component = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    it('should contain a post header', () => {
        const header = getElement(debugElement, 'jhi-post-header');
        expect(header).to.exist;
    });

    it('should contain a div with markdown content', () => {
        const header = getElement(debugElement, 'div.markdown-preview');
        expect(header).to.exist;
    });

    it('should contain a post footer', () => {
        const footer = getElement(debugElement, 'jhi-post-footer');
        expect(footer).to.exist;
    });

    it('should have correct content', () => {
        component.posting = post;
        component.ngOnInit();
        expect(component.content).to.be.equal(post.content);
    });
});
