import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CompetencyContributionComponent } from './competency-contribution.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CompetencyContributionCardComponent } from 'app/atlas/shared/competency-contribution/competency-contribution-card/competency-contribution-card.component';
import { MockComponent, MockDirective, MockModule, MockProvider } from 'ng-mocks';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { CarouselModule } from 'primeng/carousel';
import { AlertService } from 'app/shared/service/alert.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { CompetencyContributionCardDTO } from 'app/atlas/shared/entities/competency.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

describe('CompetencyContributionComponent', () => {
    let fixture: ComponentFixture<CompetencyContributionComponent>;
    let courseCompetencyService: CourseCompetencyService;
    let getCompetencyContributionsForExerciseStub: jest.SpyInstance;
    let getCompetencyContributionsForLectureUnitStub: jest.SpyInstance;
    let profileService: ProfileService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CompetencyContributionComponent, MockDirective(TranslateDirective), MockComponent(CompetencyContributionCardComponent), MockModule(CarouselModule)],
            providers: [MockProvider(CourseCompetencyService), MockProvider(AlertService), MockProvider(ProfileService)],
        }).compileComponents();

        fixture = TestBed.createComponent(CompetencyContributionComponent);

        courseCompetencyService = TestBed.inject(CourseCompetencyService);
        getCompetencyContributionsForExerciseStub = jest
            .spyOn(courseCompetencyService, 'getCompetencyContributionsForExercise')
            .mockReturnValue(of({} as HttpResponse<CompetencyContributionCardDTO[]>));
        getCompetencyContributionsForLectureUnitStub = jest
            .spyOn(courseCompetencyService, 'getCompetencyContributionsForLectureUnit')
            .mockReturnValue(of({} as HttpResponse<CompetencyContributionCardDTO[]>));

        profileService = TestBed.inject(ProfileService);
        jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
    });

    afterEach(() => {
        jest.restoreAllMocks();
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
});
