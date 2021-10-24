import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, RouterModule } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { TutorLeaderboardElement } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.model';
import { TutorLeaderboardComponent } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.component';
import { SortService } from 'app/shared/service/sort.service';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { Exam } from 'app/entities/exam.model';

describe('TutorLeaderboardComponent', () => {
    let comp: TutorLeaderboardComponent;
    let fixture: ComponentFixture<TutorLeaderboardComponent>;
    let accountService: AccountService;
    let sortService: SortService;
    let isAtLeastInstructorInCourseStub: jest.SpyInstance;
    let sortByPropertySpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(RouterModule)],
            declarations: [TutorLeaderboardComponent, MockPipe(ArtemisTranslatePipe), MockDirective(SortDirective), MockComponent(FaIconComponent)],
            providers: [
                MockProvider(AccountService),
                {
                    provide: Router,
                    useClass: MockRouter,
                },
            ],
        })
            .compileComponents()
            .then(() => {
                accountService = TestBed.inject(AccountService);
                fixture = TestBed.createComponent(TutorLeaderboardComponent);
                comp = fixture.componentInstance;
                sortService = TestBed.inject(SortService);
                isAtLeastInstructorInCourseStub = jest.spyOn(accountService, 'isAtLeastInstructorInCourse').mockReturnValue(true);
                sortByPropertySpy = jest.spyOn(sortService, 'sortByProperty').mockImplementation(() => []);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('ngOnInit', () => {
        afterEach(() => {
            jest.restoreAllMocks();
        });

        it('sets isAtLeastInstructor if course is set', () => {
            expect(comp.isAtLeastInstructor).toBe(false);
            comp.ngOnInit();
            expect(comp.isAtLeastInstructor).toBe(false);
            expect(comp.isExerciseDashboard).toBe(false);
            expect(comp.course).toBe(undefined);
            expect(isAtLeastInstructorInCourseStub).not.toBeCalled();

            const course = {} as Course;
            comp.course = course;
            comp.ngOnInit();
            expect(comp.isAtLeastInstructor).toBe(true);
            expect(comp.isExerciseDashboard).toBe(false);
            expect(comp.course).toBe(course);
            expect(isAtLeastInstructorInCourseStub).toHaveBeenCalledTimes(1);
        });

        it('sets isAtLeastInstructor if exercise.course is set', () => {
            expect(comp.isAtLeastInstructor).toBe(false);
            comp.ngOnInit();
            expect(comp.isAtLeastInstructor).toBe(false);
            expect(comp.isExerciseDashboard).toBe(false);
            expect(comp.course).toBe(undefined);
            expect(isAtLeastInstructorInCourseStub).not.toBeCalled();

            const course = {} as Course;
            comp.exercise = { course } as Exercise;
            comp.ngOnInit();
            expect(comp.isAtLeastInstructor).toBe(true);
            expect(comp.isExerciseDashboard).toBe(true);
            expect(comp.course).toBe(course);
            expect(isAtLeastInstructorInCourseStub).toHaveBeenCalledTimes(1);
        });

        it('sets isExamMode if exam is set', () => {
            expect(comp.isExamMode).toBe(false);
            comp.ngOnInit();
            expect(comp.isExamMode).toBe(false);

            expect(comp.isAtLeastInstructor).toBe(false);
            const exam = {} as Exam;
            comp.exam = exam;
            comp.ngOnInit();
            expect(comp.isExamMode).toBe(true);
        });

        it('should sort rows', () => {
            expect(sortByPropertySpy).not.toBeCalled();
            comp.ngOnInit();
            expect(sortByPropertySpy).toHaveBeenCalledTimes(1);
        });
    });

    describe('tutorData', () => {
        it('should fill the table with elements', () => {
            const element = new TutorLeaderboardElement();
            comp.tutorsData = [element];
            const table: HTMLTableElement = fixture.debugElement.nativeElement.querySelector('table');
            expect(table.tBodies).toHaveLength(1);
        });
    });
});
