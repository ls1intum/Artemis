import { vi } from 'vitest';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';
import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import {
    Competency,
    CompetencyExerciseLink,
    CompetencyLectureUnitLink,
    CompetencyProgress,
    CompetencyRelation,
    CompetencyRelationType,
    CompetencyTaxonomy,
    CompetencyWithTailRelationDTO,
    CourseCompetencyProgress,
    CourseCompetencyType,
} from 'app/atlas/shared/entities/competency.model';
import { CompetencyImportResponse } from 'app/openapi/model/competency-import-response';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
import { CourseCompetencyRequest } from 'app/openapi/model/course-competency-request';
import { CompetencyImportOptions } from 'app/openapi/model/competency-import-options';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { CompetencyPageableSearch, SearchResult, SortingOrder } from 'app/foundation/pagination/pageable-table';
import * as dateUtils from 'app/foundation/util/date.utils';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import dayjs from 'dayjs/esm';
import { Dayjs } from 'dayjs/esm/index';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockExerciseService } from 'test/helpers/mocks/service/mock-exercise.service';
import { PrerequisiteService } from 'app/atlas/manage/services/prerequisite.service';
import { Prerequisite } from 'app/atlas/shared/entities/prerequisite.model';
import { CompetencyProgressDTO, CompetencyWithTailRelationResponseDTO, CourseCompetencyResponseDTO, toPrerequisite } from 'app/atlas/shared/dto/course-competency-response.dto';

describe('PrerequisiteService', () => {
    let prerequisiteService: PrerequisiteService;
    let httpTestingController: HttpTestingController;
    let defaultPrerequisiteDtos: CourseCompetencyResponseDTO[];
    let defaultPrerequisites: Prerequisite[];
    let defaultCompetencyProgress: CompetencyProgress;
    let defaultCompetencyCourseProgress: CourseCompetencyProgress;
    let expectedResultCompetency: HttpResponse<Prerequisite>;
    let expectedResultCompetencyProgress: any;
    let expectedResultCompetencyCourseProgress: any;
    let resultImportAll: HttpResponse<CompetencyWithTailRelationDTO[]>;
    let resultImportBulk: HttpResponse<CompetencyWithTailRelationDTO[]>;
    let resultGetRelations: HttpResponse<CompetencyRelation[]>;
    let resultGetForImport: SearchResult<Prerequisite>;
    let resultImportStandardized: HttpResponse<CompetencyImportResponse[]>;

    const collectionUrl = '/api/atlas/courses/1/prerequisites';
    const dateFromServer = '2026-03-29T01:30:00+01:00';
    const dateToServer = '2026-03-29T00:30:00.000Z';

    function created(location: string) {
        return { status: 201, statusText: 'Created', headers: new HttpHeaders({ Location: location }) };
    }

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(LectureUnitService, {
                    convertLectureUnitDateFromServer<T extends LectureUnit>(unit: T): T {
                        return unit;
                    },
                    convertLectureUnitArrayDatesFromServer<T extends LectureUnit>(res: T[]): T[] {
                        return res;
                    },
                }),
                { provide: AccountService, useClass: MockAccountService },
                { provide: ExerciseService, useClass: MockExerciseService },
            ],
        });
        expectedResultCompetency = new HttpResponse<Prerequisite>();
        expectedResultCompetencyProgress = {} as HttpResponse<CompetencyProgress>;

        prerequisiteService = TestBed.inject(PrerequisiteService);
        httpTestingController = TestBed.inject(HttpTestingController);

        defaultPrerequisiteDtos = [
            {
                id: 0,
                title: 'title',
                description: 'description',
                type: CourseCompetencyType.PREREQUISITE,
                masteryThreshold: 80,
                optional: false,
                generatedByAi: false,
                taxonomy: 'APPLY',
                softDueDate: dateFromServer,
            },
        ];
        defaultPrerequisites = defaultPrerequisiteDtos.map((dto) => toPrerequisite(dto));
        defaultCompetencyProgress = { progress: 20, confidence: 50 } as CompetencyProgress;
        defaultCompetencyCourseProgress = { competencyId: 0, numberOfStudents: 8, numberOfMasteredStudents: 5, averageStudentScore: 90 } as CourseCompetencyProgress;
    });

    afterEach(() => {
        httpTestingController.verify();
    });

    it('should find a prerequisite and hydrate linked learning objects', () => {
        const setTitle = vi.spyOn(TestBed.inject(EntityTitleService), 'setTitle');
        const returnedFromService: CourseCompetencyResponseDTO = {
            ...defaultPrerequisiteDtos[0],
            id: 12,
            exerciseLinks: [{ weight: 0.5, generatedByAi: true, exercise: { id: 21, type: 'programming' } }],
            lectureUnitLinks: [{ weight: 1, generatedByAi: false, lectureUnit: { id: 31, type: 'text', content: 'Read this' } }],
        };
        prerequisiteService.findById(12, 1).subscribe((response) => (expectedResultCompetency = response));
        const req = httpTestingController.expectOne({ method: 'GET', url: `${collectionUrl}/12` });
        req.flush(returnedFromService);

        const body = expectedResultCompetency.body;
        expect(expectedResultCompetency.status).toBe(200);
        expect(body).toBeInstanceOf(Prerequisite);
        expect(body).toMatchObject({
            id: 12,
            title: 'title',
            description: 'description',
            masteryThreshold: 80,
            optional: false,
            generatedByAi: false,
        });
        expect(dayjs.isDayjs(body?.softDueDate)).toBe(true);
        expect(body?.softDueDate?.toISOString()).toBe(dateToServer);
        expect(body?.type).toBe(CourseCompetencyType.PREREQUISITE);
        expect(body?.taxonomy).toBe(CompetencyTaxonomy.APPLY);
        expect(body?.exerciseLinks?.[0]).toBeInstanceOf(CompetencyExerciseLink);
        expect(body?.exerciseLinks?.[0].weight).toBe(0.5);
        expect(body?.exerciseLinks?.[0].exercise).toBeInstanceOf(ProgrammingExercise);
        expect(body?.exerciseLinks?.[0].generatedByAi).toBe(true);
        expect(body?.lectureUnitLinks?.[0]).toBeInstanceOf(CompetencyLectureUnitLink);
        expect(body?.lectureUnitLinks?.[0].lectureUnit).toBeInstanceOf(TextUnit);
        expect(body?.lectureUnitLinks?.[0].lectureUnit).toMatchObject({ content: 'Read this' });
        expect(setTitle).toHaveBeenCalledWith(EntityType.COMPETENCY, [12], 'title');
    });

    it('should find all prerequisites and publish their titles', () => {
        const setTitle = vi.spyOn(TestBed.inject(EntityTitleService), 'setTitle');
        let result = new HttpResponse<Prerequisite[]>();
        prerequisiteService.getAllForCourse(1).subscribe((response) => (result = response));

        const req = httpTestingController.expectOne({ method: 'GET', url: collectionUrl });
        req.flush(defaultPrerequisiteDtos);

        expect(result.body).toEqual(defaultPrerequisites);
        expect(result.body?.[0]).toBeInstanceOf(Prerequisite);
        expect(result.body?.[0].exerciseLinks).toBeUndefined();
        expect(setTitle).toHaveBeenCalledWith(EntityType.COMPETENCY, [0], 'title');
    });

    it('should preserve an empty prerequisite list', () => {
        let result = new HttpResponse<Prerequisite[]>();
        prerequisiteService.getAllForCourse(1).subscribe((response) => (result = response));
        httpTestingController.expectOne({ method: 'GET', url: collectionUrl }).flush([]);

        expect(result.body).toEqual([]);
    });

    it('should get individual progress for a competency', () => {
        const returnedFromService: CompetencyProgressDTO = { ...defaultCompetencyProgress };
        prerequisiteService
            .getProgress(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultCompetencyProgress = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);

        expect(expectedResultCompetencyProgress.body).toEqual(defaultCompetencyProgress);
    });

    it('should get course progress for a competency', () => {
        const returnedFromService = { ...defaultCompetencyCourseProgress };
        prerequisiteService
            .getCourseProgress(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultCompetencyCourseProgress = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);

        expect(expectedResultCompetencyCourseProgress.body).toEqual(defaultCompetencyCourseProgress);
    });

    it('should create a prerequisite with a generated request body', () => {
        const returnedFromService = { ...defaultPrerequisiteDtos[0], id: 12 };
        const prerequisite = toPrerequisite(returnedFromService);
        prerequisite.id = undefined;
        const location = `${collectionUrl}/12`;
        const request: CourseCompetencyRequest = {
            title: 'title',
            description: 'description',
            softDueDate: dateToServer,
            masteryThreshold: 80,
            taxonomy: 'APPLY',
            optional: false,
        };

        prerequisiteService.create(prerequisite, 1).subscribe((response) => (expectedResultCompetency = response));
        const req = httpTestingController.expectOne({ method: 'POST', url: collectionUrl });
        expect(req.request.body).toEqual(request);
        req.flush(returnedFromService, created(location));

        expect(expectedResultCompetency.status).toBe(201);
        expect(expectedResultCompetency.headers.get('Location')).toBe(location);
        expect(expectedResultCompetency.body).toBeInstanceOf(Prerequisite);
        expect(expectedResultCompetency.body).toEqual(toPrerequisite(returnedFromService));
    });

    it('should reject a missing mastery threshold before sending a request', () => {
        const error = vi.fn();
        prerequisiteService.create({ title: 'title' }, 1).subscribe({ error });

        httpTestingController.expectNone(collectionUrl);
        expect(error).toHaveBeenCalledExactlyOnceWith(new Error('Cannot save a course competency without a mastery threshold'));
    });

    it('should update a prerequisite', () => {
        const returnedFromService = { ...defaultPrerequisiteDtos[0], id: 12, title: 'Test' };
        const expected = toPrerequisite(returnedFromService);
        const request: CourseCompetencyRequest = {
            id: 12,
            title: 'Test',
            description: 'description',
            softDueDate: dateToServer,
            masteryThreshold: 80,
            taxonomy: 'APPLY',
            optional: false,
        };

        prerequisiteService.update(expected, 1).subscribe((response) => (expectedResultCompetency = response));
        const req = httpTestingController.expectOne({ method: 'PUT', url: collectionUrl });
        expect(req.request.body).toEqual(request);
        req.flush(returnedFromService);

        expect(expectedResultCompetency.status).toBe(200);
        expect(expectedResultCompetency.body).toBeInstanceOf(Prerequisite);
        expect(expectedResultCompetency.body).toEqual(expected);
    });

    it('should delete a prerequisite and preserve the alert headers', () => {
        let result = new HttpResponse<void>();
        prerequisiteService.delete(12, 1).subscribe((response) => (result = response));
        const req = httpTestingController.expectOne({ method: 'DELETE', url: `${collectionUrl}/12` });
        expect(req.request.body).toBeNull();
        req.flush(null, {
            status: 200,
            statusText: 'OK',
            headers: new HttpHeaders({ 'X-artemisApp-alert': 'artemisApp.prerequisite.deleted', 'X-artemisApp-params': 'title' }),
        });

        expect(result.ok).toBe(true);
        expect(result.status).toBe(200);
        expect(result.body).toBeNull();
        expect(result.headers.get('X-artemisApp-alert')).toBe('artemisApp.prerequisite.deleted');
        expect(result.headers.get('X-artemisApp-params')).toBe('title');
    });

    it('should add a Competency relation', () => {
        const returnedFromService: CompetencyRelation = { tailCompetency: { id: 1 }, headCompetency: { id: 2 }, type: CompetencyRelationType.ASSUMES };
        const expected: CompetencyRelation = { ...returnedFromService };
        let result: any;
        prerequisiteService
            .createCompetencyRelation(expected, 1)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);

        expect(result.body).toEqual(expected);
    });

    it('should remove a Competency relation', () => {
        let result: any;
        prerequisiteService.removeCompetencyRelation(1, 1).subscribe((resp) => (result = resp.ok));
        const req = httpTestingController.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });

        expect(result).toBeTruthy();
    });

    it('should parse a list of competencies from a course description', () => {
        const description = 'Lorem ipsum dolor sit amet';
        const returnedFromService = defaultPrerequisites;
        const expected = defaultPrerequisites;
        let response: any;

        prerequisiteService.generateCompetenciesFromCourseDescription(1, description, []).subscribe((resp) => (response = resp));
        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);

        expect(response.body).toEqual(expected);
    });

    it('should import all prerequisites of a course with relations', () => {
        const wireCompetency = { ...defaultPrerequisiteDtos[0], id: 12 };
        const returnedFromService: CompetencyWithTailRelationResponseDTO[] = [
            {
                competency: wireCompetency,
                tailRelations: [{ id: 23, tailCompetencyId: 12, headCompetencyId: 13, relationType: 'ASSUMES' }],
            },
        ];
        const request: CompetencyImportOptions = {
            competencyIds: [],
            sourceCourseId: 2,
            importRelations: true,
            importExercises: false,
            importLectures: false,
        };
        const location = `${collectionUrl}/`;
        prerequisiteService.importAll(1, 2, true).subscribe((response) => (resultImportAll = response));

        const req = httpTestingController.expectOne({ method: 'POST', url: `${collectionUrl}/import-all` });
        expect(req.request.body).toEqual(request);
        req.flush(returnedFromService, created(location));

        expect(resultImportAll.status).toBe(201);
        expect(resultImportAll.headers.get('Location')).toBe(location);
        const imported = resultImportAll.body?.[0];
        expect(imported).toBeInstanceOf(CompetencyWithTailRelationDTO);
        expect(imported?.competency).toBeInstanceOf(Prerequisite);
        expect(imported?.competency).toEqual(toPrerequisite(wireCompetency));
        expect(imported?.tailRelations).toEqual([{ id: 23, tailCompetencyId: 12, headCompetencyId: 13, relationType: CompetencyRelationType.ASSUMES }]);
    });

    it('should bulk import prerequisites with no tail relations', () => {
        const wireCompetency = { ...defaultPrerequisiteDtos[0], id: 12 };
        const returnedFromService: CompetencyWithTailRelationResponseDTO[] = [{ competency: wireCompetency }];
        const request: CompetencyImportOptions = {
            competencyIds: [21],
            importRelations: false,
            importExercises: false,
            importLectures: false,
        };
        const location = `${collectionUrl}/`;
        prerequisiteService.importBulk([{ id: 21 }, {}], 1, false).subscribe((response) => (resultImportBulk = response));

        const req = httpTestingController.expectOne({ method: 'POST', url: `${collectionUrl}/import/bulk` });
        expect(req.request.body).toEqual(request);
        req.flush(returnedFromService, created(location));

        expect(resultImportBulk.status).toBe(201);
        expect(resultImportBulk.headers.get('Location')).toBe(location);
        const imported = resultImportBulk.body?.[0];
        expect(imported).toBeInstanceOf(CompetencyWithTailRelationDTO);
        expect(imported?.competency).toBeInstanceOf(Prerequisite);
        expect(imported?.competency).toEqual(toPrerequisite(wireCompetency));
        expect(imported?.tailRelations).toBeUndefined();
    });

    it('should import standardized competencies as prerequisites', () => {
        const returnedFromService: CompetencyImportResponse[] = [
            { id: 1, title: 'standardizedCompetency1', optional: false, generatedByAi: false, softDueDate: dateFromServer },
            { id: 2, title: 'standardizedCompetency2', optional: true, generatedByAi: false },
        ];
        const location = `${collectionUrl}/`;
        prerequisiteService.importStandardizedCompetencies([11, 12], 1).subscribe((response) => (resultImportStandardized = response));

        const req = httpTestingController.expectOne({ method: 'POST', url: `${collectionUrl}/import-standardized` });
        expect(req.request.body).toEqual([11, 12]);
        req.flush(returnedFromService, created(location));

        expect(resultImportStandardized.status).toBe(201);
        expect(resultImportStandardized.headers.get('Location')).toBe(location);
        expect(resultImportStandardized.body).toEqual(returnedFromService);
        expect(resultImportStandardized.body?.[0].softDueDate).toBe(dateFromServer);
    });

    it('should get competency relations', () => {
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

        expect(resultGetRelations.body).toEqual(expected);
    });

    it('should get competencies for import', () => {
        const returnedFromService: SearchResult<CourseCompetencyResponseDTO> = {
            resultsOnPage: defaultPrerequisiteDtos,
            numberOfPages: 1,
        };
        const expected = { resultsOnPage: defaultPrerequisites, numberOfPages: 1 };
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

        expect(resultGetForImport).toEqual(expected);
    });

    it('should get courseCompetency titles', () => {
        let result: HttpResponse<string[]> = new HttpResponse();
        const returnedFromService = ['title1', 'title2'];
        const expected = [...returnedFromService];

        prerequisiteService
            .getCourseCompetencyTitles(1)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);

        expect(result.body).toEqual(expected);
    });

    it('should convert response from server', () => {
        const lectureUnitService = TestBed.inject(LectureUnitService);
        const accountService = TestBed.inject(AccountService);

        const convertDateSpy = vi.spyOn(dateUtils, 'convertDateFromServer');
        const convertLectureUnitSpy = vi.spyOn(lectureUnitService, 'convertLectureUnitDateFromServer');
        const setAccessRightsCourseSpy = vi.spyOn(accountService, 'setAccessRightsForCourse');
        const setAccessRightsExerciseSpy = vi.spyOn(accountService, 'setAccessRightsForExercise');
        const convertExerciseSpy = vi.spyOn(ExerciseService, 'convertExerciseDatesFromServer');
        const parseCategoriesSpy = vi.spyOn(ExerciseService, 'parseExerciseCategories');

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

        prerequisiteService['postProcessCompetency'](undefined);
        expect(convertDateSpy).not.toHaveBeenCalled();
        expect(convertLectureUnitSpy).not.toHaveBeenCalled();
        expect(setAccessRightsCourseSpy).not.toHaveBeenCalled();
        expect(convertExerciseSpy).not.toHaveBeenCalled();
        expect(parseCategoriesSpy).not.toHaveBeenCalled();
        expect(setAccessRightsExerciseSpy).not.toHaveBeenCalled();

        prerequisiteService['postProcessCompetency'](competencyFromServer);

        expect(convertDateSpy).toHaveBeenCalled();
        expect(convertLectureUnitSpy).toHaveBeenCalledTimes(2);
        expect(setAccessRightsCourseSpy).toHaveBeenCalledOnce();
        expect(convertExerciseSpy).toHaveBeenCalledTimes(2);
        expect(parseCategoriesSpy).toHaveBeenCalledTimes(2);
        expect(setAccessRightsExerciseSpy).toHaveBeenCalledTimes(2);
    });
});
