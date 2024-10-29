import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import {
    Competency,
    CompetencyImportResponseDTO,
    CompetencyProgress,
    CompetencyRelation,
    CompetencyRelationType,
    CompetencyWithTailRelationDTO,
    CourseCompetencyProgress,
} from 'app/entities/competency.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { CompetencyPageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import * as dateUtils from 'app/utils/date.utils';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import dayjs from 'dayjs/esm';
import { Dayjs } from 'dayjs/esm/index';
import { Exercise } from 'app/entities/exercise.model';
import { MockExerciseService } from '../../helpers/mocks/service/mock-exercise.service';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { Prerequisite } from 'app/entities/prerequisite.model';

describe('PrerequisiteService', () => {
    let prerequisiteService: PrerequisiteService;
    let httpTestingController: HttpTestingController;
    let defaultPrerequisites: Prerequisite[];
    let defaultCompetencyProgress: CompetencyProgress;
    let defaultCompetencyCourseProgress: CourseCompetencyProgress;
    let expectedResultCompetency: any;
    let expectedResultCompetencyProgress: any;
    let expectedResultCompetencyCourseProgress: any;
    let resultImportAll: HttpResponse<CompetencyWithTailRelationDTO[]>;
    let resultImportBulk: HttpResponse<CompetencyWithTailRelationDTO[]>;
    let resultGetRelations: HttpResponse<CompetencyRelation[]>;
    let resultGetForImport: SearchResult<Prerequisite>;
    let resultImportStandardized: HttpResponse<CompetencyImportResponseDTO[]>;
    let resultImport: HttpResponse<Prerequisite>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
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

        prerequisiteService = TestBed.inject(PrerequisiteService);
        httpTestingController = TestBed.inject(HttpTestingController);

        defaultPrerequisites = [{ id: 0, title: 'title', description: 'description' } as Prerequisite];
        defaultCompetencyProgress = { progress: 20, confidence: 50 } as CompetencyProgress;
        defaultCompetencyCourseProgress = { competencyId: 0, numberOfStudents: 8, numberOfMasteredStudents: 5, averageStudentScore: 90 } as CourseCompetencyProgress;
    });

    afterEach(() => {
        httpTestingController.verify();
    });

    it('should find a competency', fakeAsync(() => {
        const returnedFromService = [...defaultPrerequisites];
        prerequisiteService
            .findById(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetency.body).toEqual(defaultPrerequisites);
    }));

    it('should find all competencies', fakeAsync(() => {
        const returnedFromService = [...defaultPrerequisites];
        prerequisiteService.getAllForCourse(1).subscribe((resp) => (expectedResultCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetency.body).toEqual(defaultPrerequisites);
    }));

    it('should get individual progress for a competency', fakeAsync(() => {
        const returnedFromService = { ...defaultCompetencyProgress };
        prerequisiteService
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
        prerequisiteService
            .getCourseProgress(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultCompetencyCourseProgress = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetencyCourseProgress.body).toEqual(defaultCompetencyCourseProgress);
    }));

    it('should create a Competency', fakeAsync(() => {
        const returnedFromService = { ...defaultPrerequisites.first(), id: 0 };
        const expected = { ...returnedFromService };
        prerequisiteService
            .create({}, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetency.body).toEqual(expected);
    }));

    it('should update a Competency', fakeAsync(() => {
        const returnedFromService = { ...defaultPrerequisites.first(), title: 'Test' };
        const expected = { ...returnedFromService };
        prerequisiteService
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
        prerequisiteService.delete(1, 1).subscribe((resp) => (result = resp.ok));
        const req = httpTestingController.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        tick();

        expect(result).toBeTrue();
    }));

    it('should add a Competency relation', fakeAsync(() => {
        const returnedFromService: CompetencyRelation = { tailCompetency: { id: 1 }, headCompetency: { id: 2 }, type: CompetencyRelationType.ASSUMES };
        const expected: CompetencyRelation = { ...returnedFromService };
        let result: any;
        prerequisiteService
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
        prerequisiteService.removeCompetencyRelation(1, 1).subscribe((resp) => (result = resp.ok));
        const req = httpTestingController.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        tick();

        expect(result).toBeTrue();
    }));

    it('should parse a list of competencies from a course description', fakeAsync(() => {
        const description = 'Lorem ipsum dolor sit amet';
        const returnedFromService = defaultPrerequisites;
        const expected = defaultPrerequisites;
        let response: any;

        prerequisiteService.generateCompetenciesFromCourseDescription(1, description, []).subscribe((resp) => (response = resp));
        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(response.body).toEqual(expected);
    }));

    it('should bulk create competencies', fakeAsync(() => {
        const returnedFromService = defaultPrerequisites;
        const expected = defaultPrerequisites;
        let response: any;

        prerequisiteService.createBulk(defaultPrerequisites, 1).subscribe((resp) => (response = resp));
        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(response.body).toEqual(expected);
    }));

    it('should import all competencies of a course', fakeAsync(() => {
        const competencyDTO = new CompetencyWithTailRelationDTO();
        competencyDTO.competency = { ...defaultPrerequisites.first(), id: 1 };
        competencyDTO.tailRelations = [];
        const returnedFromService = [competencyDTO];
        const expected = [...returnedFromService];

        prerequisiteService
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
        competencyDTO.competency = { ...defaultPrerequisites.first(), id: 1 };
        competencyDTO.tailRelations = [];
        const returnedFromService = [competencyDTO];
        const expected = [...returnedFromService];

        prerequisiteService
            .importBulk([defaultPrerequisites.first()!], 1, false)
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

        prerequisiteService
            .importStandardizedCompetencies([11, 12], 2)
            .pipe(take(1))
            .subscribe((resp) => (resultImportStandardized = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(resultImportStandardized.body).toEqual(expected);
    }));

    it('should import competency', fakeAsync(() => {
        const returnedFromService = defaultPrerequisites[0];
        const expected = { ...returnedFromService };

        prerequisiteService
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

        prerequisiteService
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
            resultsOnPage: defaultPrerequisites,
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

        prerequisiteService
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

        prerequisiteService
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
        const convertLectureUnitsSpy = jest.spyOn(lectureUnitService, 'convertLectureUnitArrayDatesFromServer');
        const setAccessRightsCourseSpy = jest.spyOn(accountService, 'setAccessRightsForCourse');
        const setAccessRightsExerciseSpy = jest.spyOn(accountService, 'setAccessRightsForExercise');
        const convertExercisesSpy = jest.spyOn(ExerciseService, 'convertExercisesDateFromServer');
        const parseCategoriesSpy = jest.spyOn(ExerciseService, 'parseExerciseCategories');

        const exercise: Exercise = {
            numberOfAssessmentsOfCorrectionRounds: [],
            studentAssignedTeamIdComputed: false,
            secondCorrectionEnabled: false,
        };
        const competencyFromServer: Competency = {
            softDueDate: dayjs('2022-02-20') as Dayjs,
            course: { id: 1 },
            lectureUnits: [{ id: 1 }, { id: 2 }],
            exercises: [
                { id: 3, ...exercise },
                { id: 4, ...exercise },
            ],
        };

        prerequisiteService['convertCompetencyResponseFromServer']({} as HttpResponse<Competency>);
        expect(convertDateSpy).not.toHaveBeenCalled();
        expect(convertLectureUnitsSpy).not.toHaveBeenCalled();
        expect(setAccessRightsCourseSpy).not.toHaveBeenCalled();
        expect(convertExercisesSpy).not.toHaveBeenCalled();
        expect(parseCategoriesSpy).not.toHaveBeenCalled();
        expect(setAccessRightsExerciseSpy).not.toHaveBeenCalled();

        prerequisiteService['convertCompetencyResponseFromServer']({ body: competencyFromServer } as HttpResponse<Competency>);

        expect(convertDateSpy).toHaveBeenCalled();
        expect(convertLectureUnitsSpy).toHaveBeenCalledOnce();
        expect(setAccessRightsCourseSpy).toHaveBeenCalledOnce();
        expect(convertExercisesSpy).toHaveBeenCalledOnce();
        expect(parseCategoriesSpy).toHaveBeenCalledTimes(2);
        expect(setAccessRightsExerciseSpy).toHaveBeenCalledTimes(2);
    });
});
