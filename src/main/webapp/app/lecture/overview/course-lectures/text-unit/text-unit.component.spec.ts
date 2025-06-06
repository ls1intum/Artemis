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

    let writeStub: jest.SpyInstance;
    let closeStub: jest.SpyInstance;
    let focusStub: jest.SpyInstance;
    let openStub: jest.SpyInstance;

    const textUnit: TextUnit = {
        id: 1,
        name: 'Test Text Unit',
        content: '# Sample Markdown',
        completed: false,
        visibleToStudents: true,
    };

    const exampleHtml = '<h1>Sample Markdown</h1>';

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

        writeStub = jest.spyOn(window.document, 'write').mockImplementation();
        closeStub = jest.spyOn(window.document, 'close').mockImplementation();
        focusStub = jest.spyOn(window, 'focus').mockImplementation();
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

    it('should display html in new window on isolatedView click', () => {
        const innerHtmlCopy = window.document.body.innerHTML;

        fixture.detectChanges();

        const isolatedViewButton = fixture.debugElement.query(By.css('#view-isolated-button'));
        isolatedViewButton.nativeElement.click();

        fixture.detectChanges();

        expect(openStub).toHaveBeenCalledOnce();
        expect(writeStub).toHaveBeenCalledTimes(4);
        expect(closeStub).toHaveBeenCalledOnce();
        expect(focusStub).toHaveBeenCalledOnce();
        expect(window.document.body.innerHTML).toEqual(exampleHtml);
        window.document.body.innerHTML = innerHtmlCopy;
    });

    it('should log event on isolated view', () => {
        const logEventSpy = jest.spyOn(scienceService, 'logEvent');
        component.handleIsolatedView();
        expect(logEventSpy).toHaveBeenCalledOnce();
    });
});
