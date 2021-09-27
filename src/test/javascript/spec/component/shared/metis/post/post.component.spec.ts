import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { DebugElement, Directive, Input } from '@angular/core';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { PostComponent } from 'app/shared/metis/post/post.component';
import { getElement } from '../../../../helpers/utils/general.utils';
import { PostFooterComponent } from 'app/shared/metis/postings-footer/post-footer/post-footer.component';
import { PostHeaderComponent } from 'app/shared/metis/postings-header/post-header/post-header.component';
import { metisPostExerciseUser1 } from '../../../../helpers/sample/metis-sample-data';
import { PostingContentComponent } from 'app/shared/metis/posting-content/posting-content.components';
import { MockMetisService } from '../../../../helpers/mocks/service/mock-metis-service.service';
import { MetisService } from 'app/shared/metis/metis.service';

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

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [{ provide: MetisService, useClass: MockMetisService }],
            declarations: [
                PostComponent,
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(PostHeaderComponent),
                MockComponent(PostingContentComponent),
                MockComponent(PostFooterComponent),
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
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
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
        const title = getElement(debugElement, 'p.post-title');
        expect(title).toBeDefined();
        const clickableId = getElement(debugElement, 'a.reference-hash');
        expect(clickableId).toBeDefined();
        expect(clickableId.innerHTML).toEqual(`#${metisPostExerciseUser1.id}`);
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
