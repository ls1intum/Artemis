import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MockPipe } from 'ng-mocks';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { Post } from 'app/entities/metis/post.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('PostComponent', () => {
    let component: PostComponent;
    let fixture: ComponentFixture<PostComponent>;

    const post = {
        id: 2,
        creationDate: undefined,
        content: 'content',
        title: 'title',
        tags: ['tag'],
    } as Post;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [],
            declarations: [PostComponent, MockPipe(HtmlForMarkdownPipe)],
            schemas: [NO_ERRORS_SCHEMA],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostComponent);
                component = fixture.componentInstance;
            });
    });

    it('should contain a header', () => {
        const header = fixture.debugElement.nativeElement.querySelector('jhi-post-header');
        expect(header).to.exist;
    });

    it('should contain a div with markdown content', () => {
        const header = fixture.debugElement.nativeElement.querySelector('div.markdown-preview');
        expect(header).to.exist;
    });

    it('should contain a footer', () => {
        const footer = fixture.debugElement.nativeElement.querySelector('jhi-post-footer');
        expect(footer).to.exist;
    });

    it('should have correct content', () => {
        component.posting = post;
        component.ngOnInit();
        expect(component.content).to.be.equal(post.content);
    });
});
