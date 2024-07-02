import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { MockProvider } from 'ng-mocks';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { Competency, CourseCompetency, CourseCompetencyType } from 'app/entities/competency.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { MockExerciseService } from '../../helpers/mocks/service/mock-exercise.service';
import { CourseCompetencyService } from 'app/course/competencies/course-competency.service';

describe('CourseCompetencyService', () => {
    let courseCompetencyService: CourseCompetencyService;
    let httpTestingController: HttpTestingController;
    let defaultCompetencies: Competency[];
    let expectedResultCompetency: any;

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

        courseCompetencyService = TestBed.inject(CourseCompetencyService);
        httpTestingController = TestBed.inject(HttpTestingController);

        defaultCompetencies = [
            { id: 0, title: 'title', description: 'description', type: CourseCompetencyType.COMPETENCY } as CourseCompetency,
            { id: 1, title: 'title2', description: 'description2', type: CourseCompetencyType.PREREQUISITE } as CourseCompetency,
        ];
    });

    afterEach(() => {
        httpTestingController.verify();
    });

    it('should find all competencies', fakeAsync(() => {
        const returnedFromService = [...defaultCompetencies];
        courseCompetencyService.getAllForCourse(1).subscribe((resp) => (expectedResultCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetency.body).toEqual(defaultCompetencies);
    }));
});
