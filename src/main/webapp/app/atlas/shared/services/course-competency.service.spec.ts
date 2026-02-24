import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { MockProvider } from 'ng-mocks';
import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { Competency, CompetencyWithTailRelationDTO, CourseCompetencyType } from 'app/atlas/shared/entities/competency.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { MockExerciseService } from 'test/helpers/mocks/service/mock-exercise.service';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { take } from 'rxjs/operators';
import { CompetencyWithTailRelationResponseDTO, CourseCompetencyResponseDTO, toCompetency, toPrerequisite } from 'app/atlas/shared/dto/course-competency-response.dto';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('CourseCompetencyService', () => {
    setupTestBed({ zoneless: true });
    let courseCompetencyService: CourseCompetencyService;
    let httpTestingController: HttpTestingController;
    let defaultCompetencies: CourseCompetencyResponseDTO[];
    let expectedResultCompetency: any;

    let resultImportAll: HttpResponse<CompetencyWithTailRelationDTO[]>;

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

        courseCompetencyService = TestBed.inject(CourseCompetencyService);
        httpTestingController = TestBed.inject(HttpTestingController);

        defaultCompetencies = [
            { id: 0, title: 'title', description: 'description', type: CourseCompetencyType.COMPETENCY } as CourseCompetencyResponseDTO,
            { id: 1, title: 'title2', description: 'description2', type: CourseCompetencyType.PREREQUISITE } as CourseCompetencyResponseDTO,
        ];
    });

    afterEach(() => {
        httpTestingController.verify();
    });

    it('should find all competencies', () => {
        const returnedFromService = [...defaultCompetencies];
        const expected = returnedFromService.map((dto) => (dto.type === CourseCompetencyType.PREREQUISITE ? toPrerequisite(dto) : toCompetency(dto)));
        courseCompetencyService.getAllForCourse(1).subscribe((resp) => (expectedResultCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);

        expect(expectedResultCompetency.body).toEqual(expected);
    });

    it('should find a competency', () => {
        const returnedFromService = defaultCompetencies.first()!;
        const expected = toCompetency(returnedFromService);
        courseCompetencyService
            .findById(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);

        expect(expectedResultCompetency.body).toEqual(expected);
    });

    it('should import all competencies of a course', () => {
        const competencyDTO = new CompetencyWithTailRelationDTO();
        competencyDTO.competency = toCompetency(defaultCompetencies.first()!);
        competencyDTO.tailRelations = [];
        const returnedFromService: CompetencyWithTailRelationResponseDTO[] = [{ competency: { ...defaultCompetencies.first()!, id: 1 }, tailRelations: [] }];
        const expected = [competencyDTO];

        courseCompetencyService
            .importAll(1, 2, true)
            .pipe(take(1))
            .subscribe((resp) => (resultImportAll = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);

        expect(resultImportAll.body).toEqual(expected);
    });
});
