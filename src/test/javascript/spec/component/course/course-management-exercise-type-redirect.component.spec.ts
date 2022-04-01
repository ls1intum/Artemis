import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { CourseManagementExerciseTypeRedirectComponent } from 'app/course/manage/course-management-exercise-type-redirect.component';
import { of } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { MockExerciseService } from '../../helpers/mocks/service/mock-exercise.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { HttpResponse } from '@angular/common/http';

describe('CourseManagementExerciseTypeRedirect', () => {
    let fixture: ComponentFixture<CourseManagementExerciseTypeRedirectComponent>;
    let component: CourseManagementExerciseTypeRedirectComponent;
    let router: Router;
    let exerciseService: ExerciseService;

    beforeEach(() => {
        router = {
            navigate: jest.fn(),
        } as any as Router;
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseManagementExerciseTypeRedirectComponent],
            providers: [
                { provide: ActivatedRoute, useValue: { params: of({ courseId: 1, exerciseId: 2 }) } },
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: Router, useValue: router },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseManagementExerciseTypeRedirectComponent);
                component = fixture.componentInstance;
                exerciseService = TestBed.inject(ExerciseService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it.each([ExerciseType.TEXT, ExerciseType.QUIZ, ExerciseType.PROGRAMMING, ExerciseType.MODELING, ExerciseType.FILE_UPLOAD])(
        'should redirect correctly for each exercise type',
        (type: ExerciseType) => {
            const exerciseServiceMock = jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse<Exercise>({ body: { id: 2, type } as Exercise })));
            fixture.detectChanges();

            expect(exerciseServiceMock).toHaveBeenCalledTimes(1);
            expect(exerciseServiceMock).toHaveBeenCalledWith(2);

            expect(router.navigate).toHaveBeenCalledTimes(1);
            expect(router.navigate).toHaveBeenCalledWith(['course-management', 1, type + '-exercises', 2], { replaceUrl: true });
        },
    );
});
