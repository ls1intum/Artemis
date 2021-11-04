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
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

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
            declarations: [TutorLeaderboardComponent, MockPipe(ArtemisTranslatePipe), MockDirective(SortDirective), MockComponent(FaIconComponent), MockDirective(NgbTooltip)],
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
        it('sets isAtLeastInstructor if course is set', () => {
            const course = {} as Course;
            testIsAtLeastInstructor((component) => {
                component.course = course;
            });
            expect(comp.isExerciseDashboard).toBe(false);
            expect(comp.course).toBe(course);
            expect(comp.exercise).toBe(undefined);
        });

        it('sets isAtLeastInstructor if exercise.course is set', () => {
            const course = {} as Course;
            const exercise = { course } as Exercise;
            testIsAtLeastInstructor((component) => {
                component.exercise = exercise;
            });
            expect(comp.isExerciseDashboard).toBe(true);
            expect(comp.course).toBe(course);
            expect(comp.exercise).toBe(exercise);
        });

        it('sets isExamMode if exam is set', () => {
            expect(comp.isExamMode).toBe(false);
            comp.ngOnInit();
            expect(comp.isExamMode).toBe(false);

            const exam = {} as Exam;
            const course = { exams: [exam] } as Course;
            comp.exam = exam;
            comp.course = course;
            comp.ngOnInit();
            expect(comp.isExamMode).toBe(true);
        });

        it('should sort rows', () => {
            comp.ngOnInit();
            expect(sortByPropertySpy).toHaveBeenCalledTimes(1);
        });

        function testIsAtLeastInstructor(setupComponent: (comp: TutorLeaderboardComponent) => void): void {
            expect(comp.isAtLeastInstructor).toBe(false);
            comp.ngOnInit();
            expect(comp.isAtLeastInstructor).toBe(false);
            expect(comp.isExerciseDashboard).toBe(false);
            expect(comp.course).toBe(undefined);
            expect(isAtLeastInstructorInCourseStub).not.toBeCalled();

            setupComponent(comp);
            comp.ngOnInit();
            expect(comp.isAtLeastInstructor).toBe(true);
            expect(isAtLeastInstructorInCourseStub).toHaveBeenCalledTimes(1);
        }
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
