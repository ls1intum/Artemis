import { ArtemisTestModule } from '../../../test.module';
import { MockHasAnyAuthorityDirective } from '../../../helpers/mocks/directive/mock-has-any-authority.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ExerciseScoresComponent } from 'app/exercises/shared/exercise-scores/exercise-scores.component';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { ActivatedRoute } from '@angular/router';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { Subscription, of } from 'rxjs';
import { NgModel } from '@angular/forms';
import { ExerciseScoresExportButtonComponent } from 'app/exercises/shared/exercise-scores/exercise-scores-export-button.component';
import { ProgrammingAssessmentRepoExportButtonComponent } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export-button.component';
import { SubmissionExportButtonComponent } from 'app/exercises/shared/submission-export/submission-export-button.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { Course } from 'app/entities/course.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { Result } from 'app/entities/result.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { HttpResponse } from '@angular/common/http';
import { Team } from 'app/entities/team.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { MockTranslateValuesDirective } from '../../../helpers/mocks/directive/mock-translate-values.directive';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { MockExerciseService } from '../../../helpers/mocks/service/mock-exercise.service';
import { MockResultService } from '../../../helpers/mocks/service/mock-result.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockCourseManagementService } from '../../../helpers/mocks/service/mock-course-management.service';
import { MockProgrammingSubmissionService } from '../../../helpers/mocks/service/mock-programming-submission.service';
import { Range } from 'app/shared/util/utils';

describe('Exercise Scores Component', () => {
    let component: ExerciseScoresComponent;
    let fixture: ComponentFixture<ExerciseScoresComponent>;
    let resultService: ResultService;
    let programmingSubmissionService: ProgrammingSubmissionService;
    let courseService: CourseManagementService;
    let exerciseService: ExerciseService;

    const exercise: Exercise = {
        id: 1,
        type: ExerciseType.TEXT,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };

    const course = new Course();
    course.id = 1;

    const team: Team = {
        name: 'name',
        shortName: 'shortName',
        students: [
            {
                login: 'login1',
                name: 'name1',
                internal: true,
                guidedTourSettings: [],
            },
            {
                login: 'login2',
                name: 'name2',
                internal: true,
                guidedTourSettings: [],
            },
        ],
    };

    const result = new Result();
    const participation: ProgrammingExerciseStudentParticipation = {
        buildPlanId: '1',
        userIndependentRepositoryUrl: 'url',
        participantIdentifier: 'participationId',
        participantName: 'participantName',
    };
    result.participation = participation;
    result.assessmentType = AssessmentType.MANUAL;
    const resultsToFilter = [{ score: 3 }, { score: 11 }, { score: 22 }, { score: 33 }, { score: 44 }, { score: 55 }, { score: 66 }, { score: 77 }, { score: 88 }, { score: 99 }];
    const filterRanges = [
        new Range(0, 10),
        new Range(10, 20),
        new Range(20, 30),
        new Range(30, 40),
        new Range(40, 50),
        new Range(50, 60),
        new Range(60, 70),
        new Range(70, 80),
        new Range(80, 90),
        new Range(90, 100),
    ];

    const route = {
        data: of({ courseId: 1 }),
        children: [],
        params: of({ courseId: 1, exerciseId: 2 }),
        snapshot: { queryParamMap: { get: () => undefined } },
    } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MockModule(NgxDatatableModule)],
            declarations: [
                ExerciseScoresComponent,
                MockComponent(ExerciseScoresExportButtonComponent),
                MockComponent(ProgrammingAssessmentRepoExportButtonComponent),
                MockComponent(SubmissionExportButtonComponent),
                MockComponent(DataTableComponent),
                MockComponent(ResultComponent),
                MockDirective(FeatureToggleLinkDirective),
                MockTranslateValuesDirective,
                MockHasAnyAuthorityDirective,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(NgModel),
            ],
            providers: [
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: ActivatedRoute, useValue: route },
                { provide: ResultService, useClass: MockResultService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: ProgrammingSubmissionService, useClass: MockProgrammingSubmissionService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseScoresComponent);
                component = fixture.componentInstance;
                resultService = TestBed.inject(ResultService);
                programmingSubmissionService = TestBed.inject(ProgrammingSubmissionService);
                courseService = TestBed.inject(CourseManagementService);
                exerciseService = TestBed.inject(ExerciseService);
                component.exercise = exercise;
                jest.spyOn(programmingSubmissionService, 'unsubscribeAllWebsocketTopics');
                component.paramSub = new Subscription();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should be correctly set onInit', fakeAsync(() => {
        const findCourseSpy = jest.spyOn(courseService, 'find');
        const findExerciseSpy = jest.spyOn(exerciseService, 'find');
        const getResultsMock = jest.spyOn(resultService, 'getResults').mockReturnValue(of(new HttpResponse<Result[]>({ body: resultsToFilter })));

        component.ngOnInit();
        tick();

        expect(findCourseSpy).toHaveBeenCalledOnce();
        expect(findCourseSpy).toHaveBeenCalledWith(1);
        expect(findExerciseSpy).toHaveBeenCalledOnce();
        expect(findExerciseSpy).toHaveBeenCalledWith(2);
        expect(getResultsMock).toHaveBeenCalledOnce();
        expect(getResultsMock).toHaveBeenCalledWith({ id: 2 });
        expect(component.filteredResults).toEqual(resultsToFilter);
    }));

    it('should get exercise participation link for exercise without an exercise group', () => {
        const expectedLink = ['/course-management', course.id!.toString(), 'text-exercises', exercise.id!.toString(), 'participations', '1', 'submissions'];
        component.course = course;
        component.exercise = exercise;

        const returnedLink = component.getExerciseParticipationsLink(1);

        expect(returnedLink).toEqual(expectedLink);
    });

    it('should get exercise participation link for exercise with an exercise group', () => {
        const expectedLink = ['/course-management', course.id!.toString(), 'exams', '1', 'exercise-groups', '1', 'text-exercises', exercise.id!.toString(), 'participations', '2'];
        component.course = course;
        component.exercise = exercise;
        component.exercise.exerciseGroup = {
            id: 1,
            exam: {
                id: 1,
            },
        };

        const returnedLink = component.getExerciseParticipationsLink(2);

        expect(returnedLink).toEqual(expectedLink);
    });

    it('should update result', fakeAsync(() => {
        component.updateResultFilter(component.FilterProp.SUCCESSFUL);

        expect(component.isLoading).toBeTrue();
        tick();
        expect(component.resultCriteria.filterProp).toBe(component.FilterProp.SUCCESSFUL);
        expect(component.isLoading).toBeFalse();
    }));

    it('should filter result prop "successful"', () => {
        component.resultCriteria.filterProp = component.FilterProp.SUCCESSFUL;
        result.successful = true;

        expect(component.filterResultByProp(result)).toBeTrue();
    });

    it('should filter result prop "unsuccessful"', () => {
        component.resultCriteria.filterProp = component.FilterProp.UNSUCCESSFUL;
        result.successful = true;

        expect(component.filterResultByProp(result)).toBeFalse();
    });

    it('should filter result prop "build failed"', () => {
        component.resultCriteria.filterProp = component.FilterProp.BUILD_FAILED;

        expect(component.filterResultByProp(result)).toBeFalse();
    });

    it('should filter result prop "manual"', () => {
        component.resultCriteria.filterProp = component.FilterProp.MANUAL;

        expect(component.filterResultByProp(result)).toBeTrue();
    });

    it('should filter result prop "automatic"', () => {
        component.resultCriteria.filterProp = component.FilterProp.AUTOMATIC;

        expect(component.filterResultByProp(result)).toBeFalse();
    });

    it('should filter result prop default value', () => {
        expect(component.filterResultByProp(result)).toBeTrue();
    });

    it('should handle result size change', () => {
        component.handleResultsSizeChange(10);

        expect(component.filteredResultsSize).toBe(10);
    });

    it('should return build plan id', () => {
        expect(component.buildPlanId(result)).toBe('1');
    });

    it('should return project key', () => {
        component.exercise = {
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
            projectKey: 'key',
        } as ProgrammingExercise;

        expect(component.projectKey()).toBe('key');
    });

    it('should return repository link', () => {
        expect(component.getRepositoryLink(result)).toBe('url');
    });

    it('should export names correctly for student participation', () => {
        component.results = [result];
        const rows = ['data:text/csv;charset=utf-8,participantName'];
        const resultServiceStub = jest.spyOn(resultService, 'triggerDownloadCSV');

        component.exportNames();

        expect(resultServiceStub).toHaveBeenCalledOnce();
        expect(resultServiceStub).toHaveBeenCalledWith(rows, 'results-names.csv');
    });

    it('should export names correctly for team participation', () => {
        participation.team = team;
        component.results = [result];
        const rows = ['data:text/csv;charset=utf-8,Team Name,Team Short Name,Students', 'name,shortName,"name1, name2"'];
        const resultServiceStub = jest.spyOn(resultService, 'triggerDownloadCSV');

        component.exportNames();

        expect(resultServiceStub).toHaveBeenCalledOnce();
        expect(resultServiceStub).toHaveBeenCalledWith(rows, 'results-names.csv');
        participation.team = undefined;
    });

    it('should search result for student participation', () => {
        participation.student = {
            login: 'login',
            name: 'name',
            internal: true,
            guidedTourSettings: [],
        };

        expect(component.searchResultFormatter(result)).toBe('login (name)');

        participation.student = undefined;
    });

    it('should search result for team', () => {
        participation.team = team;
        const expectedResult = 'name (shortName) ⟹ name1 (login1), name2 (login2)';

        expect(component.searchResultFormatter(result)).toBe(expectedResult);

        participation.team = undefined;
    });

    it('should search result and return empty string', () => {
        expect(component.searchResultFormatter(result)).toBe('');
    });

    it('should search text from result', () => {
        expect(component.searchTextFromResult(result)).toBe('participationId');
    });

    it('should refresh properly', () => {
        const resultServiceStub = jest.spyOn(resultService, 'getResults').mockReturnValue(of(new HttpResponse<Result[]>({ body: [result] })));

        component.refresh();

        expect(resultServiceStub).toHaveBeenCalledOnce();
        expect(resultServiceStub).toHaveBeenCalledWith(component.exercise);
        expect(component.results).toEqual([result]);
        expect(component.filteredResults).toEqual([result]);
        expect(component.isLoading).toBeFalse();
    });

    it.each(filterRanges)('should filter results correctly and reset the filter', (rangeFilter: Range) => {
        component.rangeFilter = rangeFilter;
        component.results = [result];

        const returnedResults = component.filterByScoreRange(resultsToFilter);

        expect(returnedResults).toEqual([resultsToFilter[filterRanges.indexOf(rangeFilter)]]);

        component.resetFilterOptions();

        expect(component.rangeFilter).toBeUndefined();
        expect(component.filteredResults).toEqual([result]);
    });
});
