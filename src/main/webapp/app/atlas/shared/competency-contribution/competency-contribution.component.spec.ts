import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CompetencyContributionComponent } from './competency-contribution.component';
import { MockProvider } from 'ng-mocks';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideRouter } from '@angular/router';
import { AlertService } from 'app/foundation/service/alert.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { CompetencyContributionCardDTO } from 'app/atlas/shared/entities/competency.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

describe('CompetencyContributionComponent', () => {
    let fixture: ComponentFixture<CompetencyContributionComponent>;
    let courseCompetencyService: CourseCompetencyService;
    let getCompetencyContributionsForExerciseStub: ReturnType<typeof vi.spyOn>;
    let getCompetencyContributionsForLectureUnitStub: ReturnType<typeof vi.spyOn>;
    let profileService: ProfileService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CompetencyContributionComponent],
            providers: [
                MockProvider(CourseCompetencyService),
                MockProvider(AlertService),
                MockProvider(ProfileService),
                { provide: TranslateService, useClass: MockTranslateService },
                provideRouter([]),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CompetencyContributionComponent);

        courseCompetencyService = TestBed.inject(CourseCompetencyService);
        getCompetencyContributionsForExerciseStub = vi
            .spyOn(courseCompetencyService, 'getCompetencyContributionsForExercise')
            .mockReturnValue(of({} as HttpResponse<CompetencyContributionCardDTO[]>));
        getCompetencyContributionsForLectureUnitStub = vi
            .spyOn(courseCompetencyService, 'getCompetencyContributionsForLectureUnit')
            .mockReturnValue(of({} as HttpResponse<CompetencyContributionCardDTO[]>));

        profileService = TestBed.inject(ProfileService);
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should fetch for exercise', () => {
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('isExercise', true);
        fixture.componentRef.setInput('learningObjectId', 2);

        fixture.detectChanges();

        expect(getCompetencyContributionsForExerciseStub).toHaveBeenCalledWith(2);
        expect(getCompetencyContributionsForLectureUnitStub).not.toHaveBeenCalled();
    });

    it('should fetch for lecture unit', () => {
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('isExercise', false);
        fixture.componentRef.setInput('learningObjectId', 2);

        fixture.detectChanges();

        expect(getCompetencyContributionsForLectureUnitStub).toHaveBeenCalledWith(2);
        expect(getCompetencyContributionsForExerciseStub).not.toHaveBeenCalled();
    });

    it('shows three competency links, advances one at a time and wraps in both directions', () => {
        const competencies: CompetencyContributionCardDTO[] = Array.from({ length: 4 }, (_, index) => ({
            competencyId: index + 1,
            title: `Competency ${index + 1}`,
            weight: 0.5,
            mastery: 0,
        }));
        getCompetencyContributionsForLectureUnitStub.mockReturnValue(of(new HttpResponse({ body: competencies })));
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('isExercise', false);
        fixture.componentRef.setInput('learningObjectId', 2);
        fixture.detectChanges();
        const visibleTitles = () => Array.from(fixture.nativeElement.querySelectorAll('h4')).map((heading) => (heading as HTMLElement).textContent?.trim());
        expect(visibleTitles()).toEqual(['Competency 1', 'Competency 2', 'Competency 3']);
        fixture.nativeElement.querySelector('[data-testid="competency-contribution-next"]').click();
        fixture.detectChanges();
        expect(visibleTitles()).toEqual(['Competency 2', 'Competency 3', 'Competency 4']);
        fixture.nativeElement.querySelector('[data-testid="competency-contribution-previous"]').click();
        fixture.detectChanges();
        fixture.nativeElement.querySelector('[data-testid="competency-contribution-previous"]').click();
        fixture.detectChanges();
        expect(visibleTitles()).toEqual(['Competency 4', 'Competency 1', 'Competency 2']);
        fixture.nativeElement.querySelector('[data-testid="competency-contribution-page-1"]').click();
        fixture.detectChanges();
        expect(visibleTitles()).toEqual(['Competency 2', 'Competency 3', 'Competency 4']);
    });

    it('renders every competency without navigation when there are at most three', () => {
        getCompetencyContributionsForLectureUnitStub.mockReturnValue(of(new HttpResponse({ body: [{ competencyId: 1, title: 'Only Competency', weight: 1 }] })));
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('isExercise', false);
        fixture.componentRef.setInput('learningObjectId', 2);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelectorAll('a')).toHaveLength(1);
        expect(fixture.nativeElement.querySelector('a').getAttribute('href')).toBe('/courses/1/competencies/1');
        expect(fixture.nativeElement.querySelector('[data-testid="competency-contribution-next"]')).toBeNull();
    });
});
