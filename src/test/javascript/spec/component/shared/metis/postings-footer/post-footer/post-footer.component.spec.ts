import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PostFooterComponent } from 'app/shared/metis/postings-footer/post-footer/post-footer.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostReactionsBarComponent } from 'app/shared/metis/postings-reactions-bar/post-reactions-bar/post-reactions-bar.component';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockTranslateService } from '../../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { SinonStub, stub } from 'sinon';
import { PageType } from 'app/shared/metis/metis.util';
import { getElement, getElements } from '../../../../../helpers/utils/general.utils';
import {
    metisLecture,
    metisPostExerciseUser1,
    metisPostLectureUser1,
    metisPostLectureUser2,
    metisPostOrganization,
    metisPostTechSupport,
    metisTags,
} from '../../../../../helpers/sample/metis-sample-data';

chai.use(sinonChai);
const expect = chai.expect;

describe('PostFooterComponent', () => {
    let component: PostFooterComponent;
    let fixture: ComponentFixture<PostFooterComponent>;
    let metisService: MetisService;
    let metisServiceGetPageTypeStub: SinonStub;
    const updatedTags = ['tag1', 'tag2', 'tag3'];

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(ArtemisCoursesRoutingModule)],
            providers: [
                { provide: MetisService, useClass: MockMetisService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
            declarations: [PostFooterComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), MockComponent(PostReactionsBarComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostFooterComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceGetPageTypeStub = stub(metisService, 'getPageType');
            });
    });

    it('should initialize post tags correctly', () => {
        component.posting = metisPostLectureUser2;
        component.posting.tags = metisTags;
        component.ngOnInit();
        expect(component.tags).to.deep.equal(metisTags);
    });

    it('should initialize post without tags correctly', () => {
        component.posting = metisPostExerciseUser1;
        component.ngOnInit();
        expect(component.tags).to.deep.equal([]);
    });

    it('should initialize post without context information when shown in page section', () => {
        metisServiceGetPageTypeStub.returns(PageType.PAGE_SECTION);
        component.posting = metisPostLectureUser1;
        component.ngOnInit();
        fixture.detectChanges();
        const contextLink = getElement(fixture.debugElement, 'a.linked-context-information');
        expect(contextLink).to.not.exist;
        component.posting = metisPostOrganization;
        component.ngOnChanges();
        fixture.detectChanges();
        const context = getElement(fixture.debugElement, 'div.context-information');
        expect(context).to.not.exist;
    });

    it('should update post tags correctly', () => {
        component.posting = metisPostLectureUser1;
        component.ngOnInit();
        component.posting.tags = updatedTags;
        component.ngOnChanges();
        expect(component.tags).to.deep.equal(updatedTags);
    });

    it('should have a tag shown for each post tag', () => {
        component.posting = metisPostLectureUser1;
        component.posting.tags = metisTags;
        component.ngOnInit();
        fixture.detectChanges();
        const tags = getElements(fixture.debugElement, '.post-tag');
        expect(tags).to.have.lengthOf(metisTags.length);
    });

    it('should have a course-wide context information shown in form of a text if shown in course discussion overview', () => {
        metisServiceGetPageTypeStub.returns(PageType.OVERVIEW);
        component.posting = metisPostTechSupport;
        component.ngOnInit();
        fixture.detectChanges();
        const context = getElement(fixture.debugElement, 'div.context-information');
        expect(context).to.exist;
        expect(component.contextInformation.routerLinkComponents).to.be.equal(undefined);
    });

    it('should have a lecture context information shown in form of a link if shown in course discussion overview', () => {
        metisServiceGetPageTypeStub.returns(PageType.OVERVIEW);
        component.posting = metisPostLectureUser1;
        component.ngOnInit();
        fixture.detectChanges();
        const contextLink = getElement(fixture.debugElement, 'a.linked-context-information');
        expect(component.contextInformation.routerLinkComponents).to.include('lectures');
        expect(component.contextInformation.routerLinkComponents).to.include(metisLecture.id);
        expect(component.contextInformation.displayName).to.be.equal(metisLecture.title);
        expect(contextLink).to.exist;
    });
});
