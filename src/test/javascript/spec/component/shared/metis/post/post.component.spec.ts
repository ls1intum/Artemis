import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { DebugElement } from '@angular/core';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { getElement } from '../../../../helpers/utils/general.utils';
import { PostFooterComponent } from 'app/shared/metis/posting-footer/post-footer/post-footer.component';
import { PostHeaderComponent } from 'app/shared/metis/posting-header/post-header/post-header.component';
import { PostingContentComponent } from 'app/shared/metis/posting-content/posting-content.components';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { PageType } from 'app/shared/metis/metis.util';
import { TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { metisExercise, metisLecture, metisPostExerciseUser1, metisPostLectureUser1, metisPostTechSupport } from '../../../../helpers/sample/metis-sample-data';
import { MockQueryParamsDirective, MockRouterLinkDirective } from '../../../../helpers/mocks/directive/mock-router-link.directive';
import { ArtemisTestModule } from '../../../../test.module';
import { RouterTestingModule } from '@angular/router/testing';

describe('PostComponent', () => {
    let component: PostComponent;
    let fixture: ComponentFixture<PostComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceGetLinkSpy: jest.SpyInstance;
    let metisServiceGetQueryParamsSpy: jest.SpyInstance;
    let metisServiceIsPostResolvedStub: jest.SpyInstance;
    let metisServiceGetPageTypeStub: jest.SpyInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [RouterTestingModule],
            providers: [{ provide: MetisService, useClass: MockMetisService }],
            declarations: [
                PostComponent,
                MockPipe(HtmlForMarkdownPipe),
                MockDirective(NgbTooltip),
                MockComponent(PostHeaderComponent),
                MockComponent(PostingContentComponent),
                MockComponent(PostFooterComponent),
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
                MockQueryParamsDirective,
                TranslatePipeMock,
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostComponent);
                metisService = TestBed.inject(MetisService);
                component = fixture.componentInstance;
                debugElement = fixture.debugElement;
                metisServiceIsPostResolvedStub = jest.spyOn(metisService, 'isPostResolved');
                metisServiceGetPageTypeStub = jest.spyOn(metisService, 'getPageType');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should be initialized correctly', () => {
        metisServiceIsPostResolvedStub.mockReturnValue(false);
        component.posting = metisPostExerciseUser1;
        component.ngOnInit();
        expect(component.postIsResolved).toBeFalse();
    });

    it('should be re-evaluated on changes', () => {
        // per default not resolved
        component.posting = metisPostExerciseUser1;
        metisServiceIsPostResolvedStub.mockReturnValue(false);
        component.ngOnInit();
        metisServiceIsPostResolvedStub.mockReturnValue(true);
        component.ngOnChanges();
        expect(component.postIsResolved).toBeTrue();
    });

    it('should contain a post header', () => {
        const header = getElement(debugElement, 'jhi-post-header');
        expect(header).not.toBeNull();
    });

    it('should contain a title with referencable id', () => {
        metisServiceGetLinkSpy = jest.spyOn(metisService, 'getLinkForPost');
        metisServiceGetQueryParamsSpy = jest.spyOn(metisService, 'getQueryParamsForPost');
        component.posting = metisPostExerciseUser1;
        component.ngOnInit();
        fixture.detectChanges();
        const title = getElement(debugElement, 'a.post-title');
        expect(title).toBeDefined();
        const idHash = getElement(debugElement, '.reference-hash');
        expect(idHash).toBeDefined();
        expect(idHash.innerHTML).toBe(`#${metisPostExerciseUser1.id}`);
        expect(metisServiceGetLinkSpy).toHaveBeenCalledWith(metisPostExerciseUser1);
        expect(metisServiceGetQueryParamsSpy).toHaveBeenCalledWith(metisPostExerciseUser1);
    });

    it('should initialize post without context information when shown in page section', () => {
        metisServiceGetPageTypeStub.mockReturnValue(PageType.PAGE_SECTION);
        component.posting = metisPostLectureUser1;
        component.ngOnInit();
        fixture.detectChanges();
        const contextLink = getElement(fixture.debugElement, 'a.linked-context-information');
        expect(contextLink).toBeNull();
        component.posting = metisPostExerciseUser1;
        component.ngOnChanges();
        fixture.detectChanges();
        const context = getElement(fixture.debugElement, 'span.context-information');
        expect(context).toBeNull();
    });

    it('should have a course-wide context information shown as title prefix in course discussion overview', () => {
        metisServiceGetPageTypeStub.mockReturnValue(PageType.OVERVIEW);
        component.posting = metisPostTechSupport;
        component.ngOnInit();
        fixture.detectChanges();
        const context = getElement(fixture.debugElement, 'span.context-information');
        expect(context).not.toBeNull();
        expect(component.contextInformation.routerLinkComponents).toBeUndefined();
    });

    it('should have a lecture context information shown as title prefix in course discussion overview', () => {
        metisServiceGetPageTypeStub.mockReturnValue(PageType.OVERVIEW);
        component.posting = metisPostLectureUser1;
        component.ngOnInit();
        fixture.detectChanges();
        const contextLink = getElement(fixture.debugElement, 'a.linked-context-information');
        expect(component.contextInformation.routerLinkComponents).toEqual(expect.arrayContaining(['lectures']));
        expect(component.contextInformation.routerLinkComponents).toEqual(expect.arrayContaining([metisLecture.id]));
        expect(component.contextInformation.displayName).toEqual(metisLecture.title);
        expect(contextLink).not.toBeNull();
    });

    it('should have a exercise context information shown as title prefix in course discussion overview', () => {
        metisServiceGetPageTypeStub.mockReturnValue(PageType.OVERVIEW);
        component.posting = metisPostExerciseUser1;
        component.ngOnInit();
        fixture.detectChanges();
        const contextLink = getElement(fixture.debugElement, 'a.linked-context-information');
        expect(component.contextInformation.routerLinkComponents).toEqual(expect.arrayContaining(['exercises']));
        expect(component.contextInformation.routerLinkComponents).toEqual(expect.arrayContaining([metisExercise.id]));
        expect(component.contextInformation.displayName).toEqual(metisExercise.title);
        expect(contextLink).not.toBeNull();
    });

    it('should contain the posting content', () => {
        const header = getElement(debugElement, 'jhi-posting-content');
        expect(header).not.toBeNull();
    });

    it('should contain a post footer', () => {
        const footer = getElement(debugElement, 'jhi-post-footer');
        expect(footer).not.toBeNull();
    });

    it('should have correct content and title', () => {
        component.posting = metisPostExerciseUser1;
        component.ngOnInit();
        expect(component.content).toBe(metisPostExerciseUser1.content);
        expect(component.posting.title).toBe(metisPostExerciseUser1.title);
    });
});
