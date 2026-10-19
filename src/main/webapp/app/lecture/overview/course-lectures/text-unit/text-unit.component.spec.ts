import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ScienceService } from 'app/foundation/science/science.service';
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
import { ActivatedRoute, ParamMap, Router, convertToParamMap } from '@angular/router';

describe('TextUnitComponent', () => {
    let router: Router;
    let routeStub: { snapshot: { paramMap: ParamMap } };

    let component: TextUnitComponent;
    let fixture: ComponentFixture<TextUnitComponent>;

    const textUnit: TextUnit = {
        id: 1,
        name: 'Test Text Unit',
        content: '# Sample Markdown',
        completed: false,
        visibleToStudents: true,
    };

    const exampleHtml = '<h1>Sample Markdown</h1>';

    beforeEach(async () => {
        routeStub = { snapshot: { paramMap: convertToParamMap({ lectureId: '5' }) } };
        await TestBed.configureTestingModule({
            imports: [TextUnitComponent, MockComponent(CompetencyContributionComponent)],
            providers: [
                provideHttpClient(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                { provide: ScienceService, useClass: MockScienceService },
                { provide: ActivatedRoute, useFactory: () => routeStub },
                MockProvider(CourseCompetencyService),
                MockProvider(ProfileService),
            ],
        }).compileComponents();

        const competencyService = TestBed.inject(CourseCompetencyService);
        vi.spyOn(competencyService, 'getCompetencyContributionsForLectureUnit').mockReturnValue(of({} as HttpResponse<CompetencyContributionCardDTO[]>));

        router = TestBed.inject(Router);

        fixture = TestBed.createComponent(TextUnitComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('lectureUnit', textUnit);
        fixture.componentRef.setInput('courseId', 1);
    });

    afterEach(() => {
        vi.restoreAllMocks();
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

    it('should open the text unit on its own page on isolated view click', () => {
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

        fixture.detectChanges();

        const isolatedViewButton = fixture.debugElement.query(By.css('#view-isolated-button'));
        isolatedViewButton.nativeElement.click();

        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['/courses', 1, 'lectures', 5, 'text-units', textUnit.id]);
    });

    it('should prefer the lecture of the unit over the one of the route', () => {
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        // e.g. on the competency page, whose route says nothing about the lecture of the unit
        fixture.componentRef.setInput('lectureUnit', { ...textUnit, lecture: { id: 9 } });
        fixture.detectChanges();

        component.handleIsolatedView();

        expect(navigateSpy).toHaveBeenCalledExactlyOnceWith(['/courses', 1, 'lectures', 9, 'text-units', textUnit.id]);
    });

    it('should not offer the fullscreen view when the lecture of the unit is unknown', () => {
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        routeStub.snapshot.paramMap = convertToParamMap({});

        fixture.detectChanges();
        component.handleIsolatedView();

        expect(fixture.debugElement.query(By.css('#view-isolated-button'))).toBeNull();
        expect(navigateSpy).not.toHaveBeenCalled();
    });
});
