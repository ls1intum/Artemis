import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Post } from 'app/entities/metis/post.model';
import { PostFooterComponent } from 'app/shared/metis/postings-footer/post-footer/post-footer.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostReactionsBarComponent } from 'app/shared/metis/postings-reactions-bar/post-reactions-bar/post-reactions-bar.component';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { SinonStub, stub } from 'sinon';
import { MockTranslateService } from '../../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { CourseWideContext, PageType } from 'app/shared/metis/metis.util';
import { Lecture } from 'app/entities/lecture.model';
import { getElement, getElements } from '../../../../../helpers/utils/general.utils';

chai.use(sinonChai);
const expect = chai.expect;

describe('PostFooterComponent', () => {
    let component: PostFooterComponent;
    let fixture: ComponentFixture<PostFooterComponent>;
    let post: Post;
    let metisService: MetisService;
    let metisServiceGetPageTypeStub: SinonStub;

    const initialTags = ['tag1', 'tag2'];
    const updatedTags = ['tag1', 'tag2', 'tag3'];

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(ArtemisCoursesRoutingModule)],
            providers: [
                { provide: MetisService, useClass: MockMetisService },
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
            ],
            declarations: [PostFooterComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), MockComponent(PostReactionsBarComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostFooterComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceGetPageTypeStub = stub(metisService, 'getPageType');

                post = new Post();
                post.id = 1;
                post.tags = initialTags;
                component.posting = post;
                metisServiceGetPageTypeStub.returns(PageType.PAGE_SECTION);
                component.ngOnInit();
            });
    });

    it('should initialize post tags correctly', () => {
        expect(component.tags).to.deep.equal(initialTags);
    });

    it('should initialize post without tags correctly', () => {
        component.posting.tags = undefined;
        component.ngOnInit();
        expect(component.tags).to.deep.equal([]);
    });

    it('should initialize post without context information when shown in page section', () => {
        component.posting = post;
        component.posting.courseWideContext = CourseWideContext.ORGANIZATION;
        component.ngOnChanges();
        fixture.detectChanges();
        const context = getElement(fixture.debugElement, 'div.context-information');
        expect(context).to.not.exist;
        component.posting.courseWideContext = undefined;
        component.posting.lecture = { id: 8, title: 'Lecture' } as Lecture;
        component.ngOnChanges();
        fixture.detectChanges();
        const contextLink = getElement(fixture.debugElement, 'a.context-information');
        expect(contextLink).to.not.exist;
    });

    it('should update post tags correctly', () => {
        component.posting = post;
        component.posting.tags = updatedTags;
        component.ngOnChanges();
        expect(component.tags).to.deep.equal(updatedTags);
    });

    it('should have a tag shown for each post tag', () => {
        fixture.detectChanges();
        const tags = getElements(fixture.debugElement, '.post-tag');
        expect(tags).to.have.lengthOf(initialTags.length);
    });

    it('should have a course-wide context information shown in form of a text if shown in course discussion overview', () => {
        metisServiceGetPageTypeStub.returns(PageType.OVERVIEW);
        component.posting.courseWideContext = CourseWideContext.TECH_SUPPORT;
        component.ngOnInit();
        fixture.detectChanges();
        const context = getElement(fixture.debugElement, 'div.context-information');
        expect(context).to.exist;
        expect(component.contextNavigationComponents).to.be.equal(undefined);
    });

    it('should have a lecture context information shown in form of a link if shown in course discussion overview', () => {
        metisServiceGetPageTypeStub.returns(PageType.OVERVIEW);
        component.posting = post;
        component.posting.lecture = { id: 8, title: 'Lecture' } as Lecture;
        component.ngOnInit();
        fixture.detectChanges();
        const contextLink = getElement(fixture.debugElement, 'a.context-information');
        expect(component.contextNavigationComponents).to.include('lectures');
        expect(component.contextNavigationComponents).to.include(8);
        expect(component.associatedContextName).to.be.equal('Lecture');
        expect(contextLink).to.exist;
    });
});
