import * as chai from 'chai';
import sinonChai from 'sinon-chai';
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
import { SinonSpy, spy } from 'sinon';

chai.use(sinonChai);
const expect = chai.expect;

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
    let metisServiceGetLinkSpy: SinonSpy;
    let metisServiceGetQueryParamsSpy: SinonSpy;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [{ provide: MetisService, useClass: MockMetisService }],
            declarations: [
                PostComponent,
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(PostHeaderComponent),
                MockComponent(PostingContentComponent),
                MockComponent(PostFooterComponent),
                MockDirective(MockRouterLinkDirective),
                MockDirective(MockQueryParamsDirective),
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

    it('should contain a post header', () => {
        const header = getElement(debugElement, 'jhi-post-header');
        expect(header).to.exist;
    });

    it('should contain a title with referencable id', () => {
        metisServiceGetLinkSpy = spy(metisService, 'getLinkForPost');
        metisServiceGetQueryParamsSpy = spy(metisService, 'getQueryParamsForPost');
        component.posting = metisPostExerciseUser1;
        component.ngOnInit();
        fixture.detectChanges();
        const title = getElement(debugElement, 'p.post-title');
        expect(title).to.exist;
        const clickableId = getElement(debugElement, 'a.reference-hash');
        expect(clickableId).to.exist;
        expect(clickableId.innerHTML).to.be.equal(`#${metisPostExerciseUser1.id}`);
        expect(metisServiceGetLinkSpy).to.have.been.calledWith(metisPostExerciseUser1);
        expect(metisServiceGetQueryParamsSpy).to.have.been.calledWith(metisPostExerciseUser1);
    });

    it('should contain the posting content', () => {
        const header = getElement(debugElement, 'jhi-posting-content');
        expect(header).to.exist;
    });

    it('should contain a post footer', () => {
        const footer = getElement(debugElement, 'jhi-post-footer');
        expect(footer).to.exist;
    });

    it('should have correct content and title', () => {
        component.posting = metisPostExerciseUser1;
        component.ngOnInit();
        expect(component.content).to.be.equal(metisPostExerciseUser1.content);
        expect(component.posting.title).to.be.equal(metisPostExerciseUser1.title);
    });
});
