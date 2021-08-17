import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Post } from 'app/entities/metis/post.model';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { PostFooterComponent } from 'app/shared/metis/postings-footer/post-footer/post-footer.component';
import { By } from '@angular/platform-browser';

chai.use(sinonChai);
const expect = chai.expect;

describe('PostFooterComponent', () => {
    let component: PostFooterComponent;
    let fixture: ComponentFixture<PostFooterComponent>;

    const initialTags = ['tag1', 'tag2'];
    const updatedTags = ['tag1', 'tag2', 'tag3'];

    const post = {
        id: 1,
        tags: initialTags,
    } as Post;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [],
            providers: [],
            declarations: [PostFooterComponent],
            schemas: [NO_ERRORS_SCHEMA],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostFooterComponent);
                component = fixture.componentInstance;
            });
    });

    it('should initialize post tags correctly', () => {
        component.posting = post;
        component.posting.tags = initialTags;
        component.ngOnInit();
        expect(component.tags).to.deep.equal(initialTags);
    });

    it('should initialize post without tags correctly', () => {
        component.posting = post;
        component.posting.tags = undefined;
        component.ngOnInit();
        expect(component.tags).to.deep.equal([]);
    });

    it('should update post tags correctly', () => {
        component.posting = post;
        component.posting.tags = updatedTags;
        component.ngOnChanges();
        expect(component.tags).to.deep.equal(updatedTags);
    });

    it('should have a tag shown for each post tag', () => {
        component.posting = post;
        component.posting.tags = initialTags;
        component.ngOnInit();
        fixture.detectChanges();
        const tags = fixture.debugElement.queryAll(By.css('.post-tag'));
        expect(tags).to.have.lengthOf(initialTags.length);
    });
});
