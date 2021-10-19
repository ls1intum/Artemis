import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { DebugElement, Directive, Input } from '@angular/core';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { getElement } from '../../../../helpers/utils/general.utils';
import { PostFooterComponent } from 'app/shared/metis/postings-footer/post-footer/post-footer.component';
import { PostHeaderComponent } from 'app/shared/metis/postings-header/post-header/post-header.component';
import { PostingContentComponent } from 'app/shared/metis/posting-content/posting-content.components';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { metisPostExerciseUser1 } from '../../../../helpers/sample/metis-sample-data';

// tslint:disable-next-line:directive-selector
@Directive({ selector: '[routerLink]' })
export class MockRouterLinkDirective {
    @Input('routerLink') data: any;
}

// tslint:disable-next-line:directive-selector
@Directive({ selector: '[queryParams]' })
export class MockQueryParamsDirective {
    @Input('queryParams') data: any;
}

describe('PostComponent', () => {
    let component: PostComponent;
    let fixture: ComponentFixture<PostComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceGetLinkMock: jest.SpyInstance;
    let metisServiceGetQueryParamsMock: jest.SpyInstance;
    let metisServiceIsPostResolvedMock: jest.SpyInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [{ provide: MetisService, useClass: MockMetisService }],
            declarations: [
                PostComponent,
                MockPipe(HtmlForMarkdownPipe),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
                MockComponent(PostHeaderComponent),
                MockComponent(PostingContentComponent),
                MockComponent(PostFooterComponent),
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
                MockQueryParamsDirective,
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostComponent);
                metisService = TestBed.inject(MetisService);
                component = fixture.componentInstance;
                debugElement = fixture.debugElement;
                metisServiceIsPostResolvedMock = jest.spyOn(metisService, 'isPostResolved');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should be initialized correctly', () => {
        metisServiceIsPostResolvedMock.mockReturnValue(false);
        component.posting = metisPostExerciseUser1;
        component.ngOnInit();
        expect(component.postIsResolved).toBeFalsy();
    });

    it('should be re-evaluated on changes', () => {
        // per default not resolved
        component.posting = metisPostExerciseUser1;
        metisServiceIsPostResolvedMock.mockReturnValue(false);
        component.ngOnInit();
        metisServiceIsPostResolvedMock.mockReturnValue(true);
        component.ngOnChanges();
        expect(component.postIsResolved).toBeTruthy();
    });

    it('should contain a post header', () => {
        const header = getElement(debugElement, 'jhi-post-header');
        expect(header).toBeDefined();
    });

    it('should contain a title with referencable id', () => {
        metisServiceGetLinkMock = jest.spyOn(metisService, 'getLinkForPost');
        metisServiceGetQueryParamsMock = jest.spyOn(metisService, 'getQueryParamsForPost');
        component.posting = metisPostExerciseUser1;
        component.ngOnInit();
        fixture.detectChanges();
        const title = getElement(debugElement, 'a.post-title');
        expect(title).toBeDefined();
        const idHash = getElement(debugElement, '.reference-hash');
        expect(idHash).toBeDefined();
        expect(idHash.innerHTML).toEqual(`#${metisPostExerciseUser1.id}`);
        expect(metisServiceGetLinkMock).toHaveBeenCalledWith(metisPostExerciseUser1);
        expect(metisServiceGetQueryParamsMock).toHaveBeenCalledWith(metisPostExerciseUser1);
    });

    it('should contain the posting content', () => {
        const header = getElement(debugElement, 'jhi-posting-content');
        expect(header).toBeDefined();
    });

    it('should contain a post footer', () => {
        const footer = getElement(debugElement, 'jhi-post-footer');
        expect(footer).toBeDefined();
    });

    it('should have correct content and title', () => {
        component.posting = metisPostExerciseUser1;
        component.ngOnInit();
        expect(component.content).toEqual(metisPostExerciseUser1.content);
        expect(component.posting.title).toEqual(metisPostExerciseUser1.title);
    });
});
