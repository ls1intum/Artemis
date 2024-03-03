import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TextUnitComponent } from 'app/overview/course-lectures/text-unit/text-unit.component';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { NgbCollapse, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ScienceService } from 'app/shared/science/science.service';
import { MockScienceService } from '../../../helpers/mocks/service/mock-science-service';
import { ScienceEventType } from 'app/shared/science/science.model';

describe('TextUnitFormComponent', () => {
    const exampleName = 'Test';
    const exampleMarkdown = '# Sample Markdown';
    const exampleHTML = '<h3 id="samplemarkdown">Sample Markdown</h3>';
    let textUnitComponentFixture: ComponentFixture<TextUnitComponent>;
    let textUnitComponent: TextUnitComponent;
    let textUnit: TextUnit;
    let scienceService: ScienceService;
    let logEventStub: jest.SpyInstance;

    beforeEach(() => {
        textUnit = new TextUnit();
        textUnit.id = 1;
        textUnit.name = exampleName;
        textUnit.content = exampleMarkdown;

        TestBed.configureTestingModule({
            imports: [MockDirective(NgbTooltip), MockDirective(NgbCollapse)],
            declarations: [TextUnitComponent, MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe)],
            providers: [{ provide: ScienceService, useClass: MockScienceService }],
        })
            .compileComponents()
            .then(() => {
                textUnitComponentFixture = TestBed.createComponent(TextUnitComponent);
                textUnitComponent = textUnitComponentFixture.componentInstance;
                textUnitComponent.textUnit = textUnit;
                scienceService = TestBed.inject(ScienceService);
                logEventStub = jest.spyOn(scienceService, 'logEvent');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should convert markdown to html and display it', fakeAsync(() => {
        textUnitComponentFixture.detectChanges();
        textUnitComponentFixture.whenStable().then(() => {
            expect((textUnitComponent.formattedContent as any)?.changingThisBreaksApplicationSecurity).toEqual(exampleHTML);
            const markdown = textUnitComponentFixture.debugElement.nativeElement.querySelector('.markdown-preview');
            expect(markdown).not.toBeNull();
            expect(markdown.innerHTML).toEqual(exampleHTML);
        });
    }));

    it('should collapse unit when header clicked', fakeAsync(() => {
        textUnitComponentFixture.detectChanges();
        expect(textUnitComponent.isCollapsed).toBeTrue();
        const handleCollapseSpy = jest.spyOn(textUnitComponent, 'handleCollapse');

        const header = textUnitComponentFixture.debugElement.nativeElement.querySelector('.unit-card-header');
        expect(header).not.toBeNull();
        header.click();
        tick(500);

        textUnitComponentFixture.whenStable().then(() => {
            expect(handleCollapseSpy).toHaveBeenCalledOnce();
            expect(textUnitComponent.isCollapsed).toBeFalse();
        });
    }));

    it('should display html in a new window when popup button is clicked', fakeAsync(() => {
        const innerHtmlCopy = window.document.body.innerHTML;

        const writeStub = jest.spyOn(window.document, 'write').mockImplementation();
        const closeStub = jest.spyOn(window.document, 'close').mockImplementation();
        const focusStub = jest.spyOn(window, 'focus').mockImplementation();
        const openStub = jest.spyOn(window, 'open').mockReturnValue(window);

        textUnitComponentFixture.detectChanges();
        const popButton = textUnitComponentFixture.debugElement.nativeElement.querySelector('#popupButton');
        popButton.click();
        expect(textUnitComponent).not.toBeNull();
        expect(openStub).toHaveBeenCalledOnce();
        expect(writeStub).toHaveBeenCalledTimes(4);
        expect(closeStub).toHaveBeenCalledOnce();
        expect(focusStub).toHaveBeenCalledOnce();
        expect(window.document.body.innerHTML).toEqual(exampleHTML);
        window.document.body.innerHTML = innerHtmlCopy;
    }));

    it('should call completion callback when expanded', () => {
        return new Promise<void>((done) => {
            textUnitComponent.onCompletion.subscribe((event) => {
                expect(event.lectureUnit).toEqual(textUnit);
                expect(event.completed).toBeTrue();
                done();
            });
            textUnitComponent.handleCollapse(new Event('click'));
        });
    }, 1000);

    it('should call completion callback when clicked', () => {
        return new Promise<void>((done) => {
            textUnitComponent.onCompletion.subscribe((event) => {
                expect(event.lectureUnit).toEqual(textUnit);
                expect(event.completed).toBeFalse();
                done();
            });
            textUnitComponent.handleClick(new Event('click'), false);
        });
    }, 1000);

    it('should log event on open', () => {
        textUnitComponent.isCollapsed = true;
        textUnitComponentFixture.detectChanges(); // ngInit
        textUnitComponent.handleCollapse(new Event('click'));
        expect(logEventStub).toHaveBeenCalledExactlyOnceWith(ScienceEventType.LECTURE__OPEN_UNIT, textUnit.id!);
    });
});
