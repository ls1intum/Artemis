import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { Competency, CompetencyProgress, CompetencyRelation, CompetencyRelationType, CompetencyWithTailRelationDTO, CourseCompetencyProgress } from 'app/entities/competency.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { CompetencyPageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import * as dateUtils from 'app/utils/date.utils';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import dayjs from 'dayjs';
import { Dayjs } from 'dayjs/esm/index';
import { Exercise } from 'app/entities/exercise.model';
import { MockExerciseService } from '../../helpers/mocks/service/mock-exercise.service';

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

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
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

    it('should find all prerequisites', fakeAsync(() => {
        const returnedFromService = [...defaultCompetencies];
        competencyService.getAllPrerequisitesForCourse(1).subscribe((resp) => (expectedResultCompetency = resp));

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
            .create(new Competency(), 1)
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

    it('should add a prerequisite', fakeAsync(() => {
        const returnedFromService = { ...defaultCompetencies.first(), id: 0 };
        const expected = { ...returnedFromService };
        competencyService
            .addPrerequisite(defaultCompetencies.first()!.id!, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetency.body).toEqual(expected);
    }));

    it('should remove a prerequisite', fakeAsync(() => {
        let result: any;
        competencyService.removePrerequisite(1, 1).subscribe((resp) => (result = resp.ok));
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

        competencyService.generateCompetenciesFromCourseDescription(description, 1).subscribe((resp) => (response = resp));
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

    it('should get competency relations', fakeAsync(() => {
        const relation: CompetencyRelation = {
            id: 1,
            type: CompetencyRelationType.RELATES,
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

    it('convert response from server', () => {
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

        competencyService.convertCompetencyResponseFromServer({} as HttpResponse<Competency>);
        expect(convertDateSpy).not.toHaveBeenCalled();
        expect(convertLectureUnitsSpy).not.toHaveBeenCalled();
        expect(setAccessRightsCourseSpy).not.toHaveBeenCalled();
        expect(convertExercisesSpy).not.toHaveBeenCalled();
        expect(parseCategoriesSpy).not.toHaveBeenCalled();
        expect(setAccessRightsExerciseSpy).not.toHaveBeenCalled();

        competencyService.convertCompetencyResponseFromServer({ body: competencyFromServer } as HttpResponse<Competency>);

        expect(convertDateSpy).toHaveBeenCalled();
        expect(convertLectureUnitsSpy).toHaveBeenCalledOnce();
        expect(setAccessRightsCourseSpy).toHaveBeenCalledOnce();
        expect(convertExercisesSpy).toHaveBeenCalledOnce();
        expect(parseCategoriesSpy).toHaveBeenCalledTimes(2);
        expect(setAccessRightsExerciseSpy).toHaveBeenCalledTimes(2);
    });
});
