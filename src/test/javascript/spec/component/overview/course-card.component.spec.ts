import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { CourseCardComponent } from 'app/overview/course-card.component';
import { Course } from 'app/entities/course.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { Exercise } from 'app/entities/exercise.model';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { SubmissionExerciseType } from 'app/entities/submission.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { PieChartModule } from '@swimlane/ngx-charts';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ScoresStorageService } from 'app/course/course-scores/scores-storage.service';
import { CourseScores } from 'app/course/course-scores/course-scores';

describe('CourseCardComponent', () => {
    let fixture: ComponentFixture<CourseCardComponent>;
    let component: CourseCardComponent;
    let scoresStorageService: ScoresStorageService;
    const submission: ProgrammingSubmission = {
        submissionExerciseType: SubmissionExerciseType.PROGRAMMING,
        id: 3,
        submitted: true,
        results: [{ successful: true }],
    };
    const pastExercise = { id: 1, dueDate: dayjs().subtract(2, 'days') } as Exercise;
    const nextExercise = { id: 2, dueDate: dayjs().add(2, 'days'), studentParticipations: [{ submissions: [submission] }] } as Exercise;
    const secondNextExercise = { id: 3, dueDate: dayjs().add(4, 'days') } as Exercise;
    const course = { id: 1, exercises: [pastExercise, nextExercise, secondNextExercise], lectures: [], exams: [] } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(PieChartModule)],
            declarations: [
                CourseCardComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisTimeAgoPipe),
                MockRouterLinkDirective,
                MockComponent(SecuredImageComponent),
                MockDirective(TranslateDirective),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseCardComponent);
                component = fixture.componentInstance;
                scoresStorageService = TestBed.inject(ScoresStorageService);
                component.course = course;
            });
    });

    it('should initialize component', () => {
        fixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should display the next exercise which is not yet successful', () => {
        fixture.detectChanges();
        component.ngOnChanges();
        expect(component.nextRelevantExercise).toEqual(secondNextExercise);
    });

    it('should display the total course scores returned from the scores storage service', () => {
        const mockCourseScores: CourseScores = new CourseScores(0, 20, 0, { absoluteScore: 4, relativeScore: 0.3, currentRelativeScore: 0.2, presentationScore: 0 });
        jest.spyOn(scoresStorageService, 'getStoredTotalScores').mockReturnValue(mockCourseScores);

        fixture.detectChanges();
        component.ngOnChanges();

        expect(component.totalRelativeScore).toBe(0.2);
        expect(component.totalAbsoluteScore).toBe(4);
        expect(component.totalReachableScore).toBe(20);
    });
});
