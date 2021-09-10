import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Post } from 'app/entities/metis/post.model';
import { PostFooterComponent } from 'app/shared/metis/postings-footer/post-footer/post-footer.component';
import { By } from '@angular/platform-browser';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostReactionsBarComponent } from 'app/shared/metis/postings-reactions-bar/post-reactions-bar/post-reactions-bar.component';

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
            declarations: [PostFooterComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), MockComponent(PostReactionsBarComponent)],
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
