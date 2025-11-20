import { ScienceService } from 'app/shared/science/science.service';
import { TextUnitComponent } from 'app/lecture/overview/course-lectures/text-unit/text-unit.component';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { MockScienceService } from 'test/helpers/mocks/service/mock-science-service';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { CompetencyContributionComponent } from 'app/atlas/shared/competency-contribution/competency-contribution.component';
import { CompetencyContributionCardDTO } from 'app/atlas/shared/entities/competency.model';
import { of } from 'rxjs';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

describe('TextUnitComponent', () => {
    let scienceService: ScienceService;

    let component: TextUnitComponent;
    let fixture: ComponentFixture<TextUnitComponent>;

    let openStub: jest.SpyInstance;

    const textUnit: TextUnit = {
        id: 1,
        name: 'Test Text Unit',
        content: '# Sample Markdown',
        completed: false,
        visibleToStudents: true,
    };

    const exampleHtml = '<h1>Sample Markdown</h1>';

    // minimal fake window & document for the isolated view
    function makeStubWindow() {
        const created: any[] = [];
        const head = { children: [] as any[], appendChild: (n: any) => head.children.push(n) };
        const body = { className: '', innerHTML: '' };
        const doc = {
            title: '',
            head,
            body,
            readyState: 'complete', // ensures immediate run (no load listener needed)
            createElement: (tag: string) => {
                const el: any = { tagName: tag.toUpperCase() };
                // minimal link support
                el.rel = '';
                el.href = '';
                created.push(el);
                return el;
            },
            addEventListener: jest.fn(), // not used when readyState === 'complete'
        };

        return {
            document: doc as unknown as Document,
            focus: jest.fn(),
            __created: created, // for assertions if needed
        } as unknown as Window;
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TextUnitComponent, MockComponent(CompetencyContributionComponent)],
            providers: [
                provideHttpClient(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                { provide: ScienceService, useClass: MockScienceService },
                MockProvider(CourseCompetencyService),
                MockProvider(ProfileService),
            ],
        }).compileComponents();

        const competencyService = TestBed.inject(CourseCompetencyService);
        jest.spyOn(competencyService, 'getCompetencyContributionsForLectureUnit').mockReturnValue(of({} as HttpResponse<CompetencyContributionCardDTO[]>));

        scienceService = TestBed.inject(ScienceService);

        openStub = jest.spyOn(window, 'open').mockReturnValue(window);

        fixture = TestBed.createComponent(TextUnitComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('lectureUnit', textUnit);
        fixture.componentRef.setInput('courseId', 1);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    it('should convert markdown to html', () => {
        fixture.detectChanges();
        const lectureUnitToggleButton = fixture.debugElement.query(By.css('#lecture-unit-toggle-button'));
        lectureUnitToggleButton.nativeElement.click();

        fixture.detectChanges();

        const markdown = fixture.debugElement.query(By.css('.markdown-preview'));
        expect(markdown).not.toBeNull();
        expect(markdown.nativeElement.innerHTML).toEqual(exampleHtml);
    });

    it('should display html in a new window on isolated view click', () => {
        const fakeWin = makeStubWindow();
        openStub = jest.spyOn(window, 'open').mockReturnValue(fakeWin);

        fixture.detectChanges();

        const isolatedViewButton = fixture.debugElement.query(By.css('#view-isolated-button'));
        isolatedViewButton.nativeElement.click();

        // assertions against the stub window (not the real document)
        expect(openStub).toHaveBeenCalledWith('', '_blank');
        expect((fakeWin as any).focus).toHaveBeenCalledOnce();
        expect(fakeWin.document.title).toBe(textUnit.name);
        expect(fakeWin.document.body.className).toBe('markdown-body');
        expect(fakeWin.document.body.innerHTML).toBe(exampleHtml);

        // optional: verify stylesheet link was appended
        const links = (fakeWin.document.head as any).children.filter((n: any) => n.tagName === 'LINK');
        expect(links).toHaveLength(1);
        expect(links[0].rel).toBe('stylesheet');
        expect(links[0].href).toContain('public/content/github-markdown.css');
    });

    it('should log event on isolated view', () => {
        const logEventSpy = jest.spyOn(scienceService, 'logEvent');
        // use a fresh stub window so handleIsolatedView() can run without touching real window
        const fakeWin = makeStubWindow();
        jest.spyOn(window, 'open').mockReturnValue(fakeWin);

        component.handleIsolatedView();

        expect(logEventSpy).toHaveBeenCalledOnce();
    });
});
