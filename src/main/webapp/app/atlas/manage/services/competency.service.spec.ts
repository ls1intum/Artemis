import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lectureUnit.service';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';
import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import {
    Competency,
    CompetencyExerciseLink,
    CompetencyImportResponseDTO,
    CompetencyLectureUnitLink,
    CompetencyProgress,
    CompetencyRelation,
    CompetencyRelationType,
    CompetencyWithTailRelationDTO,
    CourseCompetencyProgress,
} from 'app/atlas/shared/entities/competency.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { CompetencyPageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import * as dateUtils from 'app/shared/util/date.utils';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import dayjs from 'dayjs/esm';
import { Dayjs } from 'dayjs/esm/index';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockExerciseService } from 'test/helpers/mocks/service/mock-exercise.service';

describe('CompetencyService', () => {
    let competencyService: CompetencyService;
    let httpTestingController: HttpTestingController;
    let defaultCompetencies: Competency[];
    let defaultCompetencyProgress: CompetencyProgress;
    let defaultCompetencyCourseProgress: CourseCompetencyProgress;
    let expectedResultCompetency: any;
    let expectedResultCompetencyProgress: any;
    let expectedResultCompetencyCourseProgress: any;
    let resultImportAll: HttpResponse<CompetencyWithTailRelationDTO[]>;
    let resultImportBulk: HttpResponse<CompetencyWithTailRelationDTO[]>;
    let resultGetRelations: HttpResponse<CompetencyRelation[]>;
    let resultGetForImport: SearchResult<Competency>;
    let resultImportStandardized: HttpResponse<CompetencyImportResponseDTO[]>;
    let resultImport: HttpResponse<Competency>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(LectureUnitService, {
                    convertLectureUnitArrayDatesFromServer<T extends LectureUnit>(res: T[]): T[] {
                        return res;
                    },
                    convertLectureUnitArrayDatesFromClient<T extends LectureUnit>(lectureUnits: T[]): T[] {
                        return lectureUnits;
                    },
                }),
                { provide: AccountService, useClass: MockAccountService },
                { provide: ExerciseService, useClass: MockExerciseService },
            ],
        });
        expectedResultCompetency = {} as HttpResponse<Competency>;
        expectedResultCompetencyProgress = {} as HttpResponse<CompetencyProgress>;

        competencyService = TestBed.inject(CompetencyService);
        httpTestingController = TestBed.inject(HttpTestingController);

        defaultCompetencies = [{ id: 0, title: 'title', description: 'description' } as Competency];
        defaultCompetencyProgress = { progress: 20, confidence: 50 } as CompetencyProgress;
        defaultCompetencyCourseProgress = { competencyId: 0, numberOfStudents: 8, numberOfMasteredStudents: 5, averageStudentScore: 90 } as CourseCompetencyProgress;
    });

    afterEach(() => {
        httpTestingController.verify();
    });

    it('should find a competency', fakeAsync(() => {
        const returnedFromService = [...defaultCompetencies];
        competencyService
            .findById(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetency.body).toEqual(defaultCompetencies);
    }));

    it('should find all competencies', fakeAsync(() => {
        const returnedFromService = [...defaultCompetencies];
        competencyService.getAllForCourse(1).subscribe((resp) => (expectedResultCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetency.body).toEqual(defaultCompetencies);
    }));

    it('should get individual progress for a competency', fakeAsync(() => {
        const returnedFromService = { ...defaultCompetencyProgress };
        competencyService
            .getProgress(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultCompetencyProgress = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetencyProgress.body).toEqual(defaultCompetencyProgress);
    }));

    it('should get course progress for a competency', fakeAsync(() => {
        const returnedFromService = { ...defaultCompetencyCourseProgress };
        competencyService
            .getCourseProgress(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultCompetencyCourseProgress = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetencyCourseProgress.body).toEqual(defaultCompetencyCourseProgress);
    }));

    it('should create a Competency', fakeAsync(() => {
        const returnedFromService = { ...defaultCompetencies.first(), id: 0 };
        const expected = { ...returnedFromService };
        competencyService
            .create({}, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetency.body).toEqual(expected);
    }));

    it('should update a Competency', fakeAsync(() => {
        const returnedFromService = { ...defaultCompetencies.first(), title: 'Test' };
        const expected = { ...returnedFromService };
        competencyService
            .update(expected, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetency.body).toEqual(expected);
    }));

    it('should delete a Competency', fakeAsync(() => {
        let result: any;
        competencyService.delete(1, 1).subscribe((resp) => (result = resp.ok));
        const req = httpTestingController.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        tick();

        expect(result).toBeTrue();
    }));

    it('should add a Competency relation', fakeAsync(() => {
        const returnedFromService: CompetencyRelation = { tailCompetency: { id: 1 }, headCompetency: { id: 2 }, type: CompetencyRelationType.ASSUMES };
        const expected: CompetencyRelation = { ...returnedFromService };
        let result: any;
        competencyService
            .createCompetencyRelation(expected, 1)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(result.body).toEqual(expected);
    }));

    it('should remove a Competency relation', fakeAsync(() => {
        let result: any;
        competencyService.removeCompetencyRelation(1, 1).subscribe((resp) => (result = resp.ok));
        const req = httpTestingController.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        tick();

        expect(result).toBeTrue();
    }));

    it('should parse a list of competencies from a course description', fakeAsync(() => {
        const description = 'Lorem ipsum dolor sit amet';
        const returnedFromService = defaultCompetencies;
        const expected = defaultCompetencies;
        let response: any;

        competencyService.generateCompetenciesFromCourseDescription(1, description, []).subscribe((resp) => (response = resp));
        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(response.body).toEqual(expected);
    }));

    it('should bulk create competencies', fakeAsync(() => {
        const returnedFromService = defaultCompetencies;
        const expected = defaultCompetencies;
        let response: any;

        competencyService.createBulk(defaultCompetencies, 1).subscribe((resp) => (response = resp));
        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(response.body).toEqual(expected);
    }));

    it('should import all competencies of a course', fakeAsync(() => {
        const competencyDTO = new CompetencyWithTailRelationDTO();
        competencyDTO.competency = { ...defaultCompetencies.first(), id: 1 };
        competencyDTO.tailRelations = [];
        const returnedFromService = [competencyDTO];
        const expected = [...returnedFromService];

        competencyService
            .importAll(1, 2, true)
            .pipe(take(1))
            .subscribe((resp) => (resultImportAll = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(resultImportAll.body).toEqual(expected);
    }));

    it('should bulk import competencies', fakeAsync(() => {
        const competencyDTO = new CompetencyWithTailRelationDTO();
        competencyDTO.competency = { ...defaultCompetencies.first(), id: 1 };
        competencyDTO.tailRelations = [];
        const returnedFromService = [competencyDTO];
        const expected = [...returnedFromService];

        competencyService
            .importBulk([defaultCompetencies.first()!], 1, false)
            .pipe(take(1))
            .subscribe((resp) => (resultImportBulk = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(resultImportBulk.body).toEqual(expected);
    }));

    it('should import standardized competencies', fakeAsync(() => {
        const returnedFromService: CompetencyImportResponseDTO[] = [
            { id: 1, title: 'standardizedCompetency1' },
            { id: 2, title: 'standardizedCompetency2' },
        ];
        const expected = [...returnedFromService];

        competencyService
            .importStandardizedCompetencies([11, 12], 2)
            .pipe(take(1))
            .subscribe((resp) => (resultImportStandardized = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(resultImportStandardized.body).toEqual(expected);
    }));

    it('should import competency', fakeAsync(() => {
        const returnedFromService = defaultCompetencies[0];
        const expected = { ...returnedFromService };

        competencyService
            .import(expected, 2)
            .pipe(take(1))
            .subscribe((resp) => (resultImport = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(resultImport.body).toEqual(expected);
    }));

    it('should get competency relations', fakeAsync(() => {
        const relation: CompetencyRelation = {
            id: 1,
            type: CompetencyRelationType.ASSUMES,
        };
        const returnedFromService = [relation];
        const expected = [...returnedFromService];

        competencyService
            .getCompetencyRelations(1)
            .pipe(take(1))
            .subscribe((resp) => (resultGetRelations = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(resultGetRelations.body).toEqual(expected);
    }));

    it('should get competencies for import', fakeAsync(() => {
        const returnedFromService: SearchResult<Competency> = {
            resultsOnPage: defaultCompetencies,
            numberOfPages: 1,
        };
        const expected = { ...returnedFromService };
        const search: CompetencyPageableSearch = {
            courseTitle: '',
            description: '',
            title: '',
            semester: '',
            page: 1,
            pageSize: 1,
            sortingOrder: SortingOrder.DESCENDING,
            sortedColumn: '',
        };

        competencyService
            .getForImport(search)
            .pipe(take(1))
            .subscribe((resp) => (resultGetForImport = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(resultGetForImport).toEqual(expected);
    }));

    it('should get courseCompetency titles', fakeAsync(() => {
        let result: HttpResponse<string[]> = new HttpResponse();
        const returnedFromService = ['title1', 'title2'];
        const expected = [...returnedFromService];

        competencyService
            .getCourseCompetencyTitles(1)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(result.body).toEqual(expected);
    }));

    it('should convert response from server', () => {
        const lectureUnitService = TestBed.inject(LectureUnitService);
        const accountService = TestBed.inject(AccountService);

        const convertDateSpy = jest.spyOn(dateUtils, 'convertDateFromServer');
        const convertLectureUnitSpy = jest.spyOn(lectureUnitService, 'convertLectureUnitDateFromServer');
        const setAccessRightsCourseSpy = jest.spyOn(accountService, 'setAccessRightsForCourse');
        const setAccessRightsExerciseSpy = jest.spyOn(accountService, 'setAccessRightsForExercise');
        const convertExerciseSpy = jest.spyOn(ExerciseService, 'convertExerciseDatesFromServer');
        const parseCategoriesSpy = jest.spyOn(ExerciseService, 'parseExerciseCategories');

        const exercise: Exercise = {
            numberOfAssessmentsOfCorrectionRounds: [],
            studentAssignedTeamIdComputed: false,
            secondCorrectionEnabled: false,
        };
        const competencyFromServer: Competency = {
            softDueDate: dayjs('2022-02-20') as Dayjs,
            course: { id: 1 },
            lectureUnitLinks: [new CompetencyLectureUnitLink(this, { id: 1 }, 1), new CompetencyLectureUnitLink(this, { id: 2 }, 1)],
            exerciseLinks: [new CompetencyExerciseLink(this, { id: 3, ...exercise }, 1), new CompetencyExerciseLink(this, { id: 4, ...exercise }, 1)],
        };

        competencyService['convertCompetencyResponseFromServer']({} as HttpResponse<Competency>);
        expect(convertDateSpy).not.toHaveBeenCalled();
        expect(convertLectureUnitSpy).not.toHaveBeenCalled();
        expect(setAccessRightsCourseSpy).not.toHaveBeenCalled();
        expect(convertExerciseSpy).not.toHaveBeenCalled();
        expect(parseCategoriesSpy).not.toHaveBeenCalled();
        expect(setAccessRightsExerciseSpy).not.toHaveBeenCalled();

        competencyService['convertCompetencyResponseFromServer']({ body: competencyFromServer } as HttpResponse<Competency>);

        expect(convertDateSpy).toHaveBeenCalled();
        expect(convertLectureUnitSpy).toHaveBeenCalledTimes(2);
        expect(setAccessRightsCourseSpy).toHaveBeenCalledOnce();
        expect(convertExerciseSpy).toHaveBeenCalledTimes(2);
        expect(parseCategoriesSpy).toHaveBeenCalledTimes(2);
        expect(setAccessRightsExerciseSpy).toHaveBeenCalledTimes(2);
    });
});
