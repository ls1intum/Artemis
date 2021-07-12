import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TextUnitComponent } from 'app/overview/course-lectures/text-unit/text-unit.component';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbCollapse, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';

chai.use(sinonChai);
const expect = chai.expect;
describe('TextUnitFormComponent', () => {
    const sandbox = sinon.createSandbox();
    const exampleName = 'Test';
    const exampleMarkdown = '# Sample Markdown';
    const exampleHTML = '<h1>Sample Markdown</h1>';
    let textUnitComponentFixture: ComponentFixture<TextUnitComponent>;
    let textUnitComponent: TextUnitComponent;
    let textUnit: TextUnit;

    beforeEach(() => {
        textUnit = new TextUnit();
        textUnit.name = exampleName;
        textUnit.content = exampleMarkdown;

        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                TextUnitComponent,
                MockComponent(FaIconComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbCollapse),
                MockDirective(NgbTooltip),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [
                MockProvider(ArtemisMarkdownService, {
                    safeHtmlForMarkdown: () => exampleHTML,
                    htmlForMarkdown: () => exampleHTML,
                }),
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                textUnitComponentFixture = TestBed.createComponent(TextUnitComponent);
                textUnitComponent = textUnitComponentFixture.componentInstance;
            });
    });

    afterEach(function () {
        sandbox.restore();
    });

    it('should initialize', () => {
        textUnitComponentFixture.detectChanges();
        expect(textUnitComponent).to.be.ok;
    });

    it('should convert markdown to html and display it', () => {
        textUnitComponent.textUnit = textUnit;
        textUnitComponentFixture.detectChanges();

        expect(textUnitComponent.formattedContent).to.equal(exampleHTML);
        const markdown = textUnitComponentFixture.debugElement.nativeElement.querySelector('.markdown-preview');
        expect(markdown).to.be.ok;
        expect(markdown.innerHTML).to.equal(exampleHTML);
    });

    it('should collapse unit when header clicked', () => {
        textUnitComponent.textUnit = textUnit;
        textUnitComponentFixture.detectChanges();
        expect(textUnitComponent.isCollapsed).to.be.true;
        const handleCollapseSpy = sinon.spy(textUnitComponent, 'handleCollapse');

        const header = textUnitComponentFixture.debugElement.nativeElement.querySelector('.unit-card-header');
        expect(header).to.be.ok;
        header.click();

        textUnitComponentFixture.whenStable().then(() => {
            expect(handleCollapseSpy).to.have.been.called;
            expect(textUnitComponent.isCollapsed).to.be.false;
        });

        handleCollapseSpy.restore();
    });

    it('should display html in a new window when popup button is clicked', () => {
        const contentOfNewWindow: string[] = [];
        const innerHtmlCopy = window.document.body.innerHTML;

        const writeStub = sandbox.stub(window.document, 'write').callsFake((content: string) => {
            contentOfNewWindow.push(content);
        });
        const closeStub = sandbox.stub(window.document, 'close');
        const focusStub = sandbox.stub(window, 'focus');
        const openStub = sandbox.stub(window, 'open').returns(window);

        textUnitComponent.textUnit = textUnit;
        textUnitComponentFixture.detectChanges();
        const popButton = textUnitComponentFixture.debugElement.nativeElement.querySelector('#popupButton');
        popButton.click();
        textUnitComponentFixture.whenStable().then(() => {
            expect(textUnitComponent).to.be.ok;
            expect(openStub).to.have.been.calledOnce;
            expect(writeStub).to.have.callCount(4);
            expect(closeStub).to.have.been.calledOnce;
            expect(focusStub).to.have.been.calledOnce;
            expect(window.document.body.innerHTML).to.equal(exampleHTML);
            window.document.body.innerHTML = innerHtmlCopy;
        });
    });
});
