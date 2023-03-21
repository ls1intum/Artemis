import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTestModule } from '../../../test.module';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { MockCourseExerciseService } from '../../../helpers/mocks/service/mock-course-exercise.service';
import { MockParticipationWebsocketService } from '../../../helpers/mocks/service/mock-participation-websocket.service';
import { Result } from 'app/entities/result.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import dayjs from 'dayjs/esm';
import { MockCourseService } from '../../../helpers/mocks/service/mock-course.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { InitializationState } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { Course } from 'app/entities/course.model';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { NotReleasedTagComponent } from 'app/shared/components/not-released-tag.component';
import { DifficultyBadgeComponent } from 'app/exercises/shared/exercise-headers/difficulty-badge.component';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { RouterTestingModule } from '@angular/router/testing';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { ExerciseCategoriesComponent } from 'app/shared/exercise-categories/exercise-categories.component';

@Component({
    template: '',
})
class DummyComponent {}

describe('CourseExerciseRowComponent', () => {
    let comp: CourseExerciseRowComponent;
    let fixture: ComponentFixture<CourseExerciseRowComponent>;
    let debugElement: DebugElement;
    let getAllParticipationsStub: jest.SpyInstance;
    let participationWebsocketService: ParticipationWebsocketService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                TranslateModule.forRoot(),
                NgbModule,
                RouterTestingModule.withRoutes([
                    { path: 'courses/:courseId/exercises', component: DummyComponent },
                    { path: 'courses/:courseId/exercises/:exerciseId', component: DummyComponent },
                ]),
            ],
            declarations: [
                MockComponent(SubmissionResultStatusComponent),
                MockComponent(ExerciseDetailsStudentActionsComponent),
                MockComponent(NotReleasedTagComponent),
                MockComponent(DifficultyBadgeComponent),
                MockComponent(ExerciseCategoriesComponent),
                MockComponent(IncludedInScoreBadgeComponent),
                MockPipe(ArtemisTimeAgoPipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(OrionFilterDirective),
                CourseExerciseRowComponent,
                DummyComponent,
            ],
            providers: [
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: CourseManagementService, useClass: MockCourseService },
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseExerciseRowComponent);
                comp = fixture.componentInstance;
                comp.course = { id: 123, isAtLeastInstructor: true } as Course;
                debugElement = fixture.debugElement;
                participationWebsocketService = debugElement.injector.get(ParticipationWebsocketService);
                getAllParticipationsStub = jest.spyOn(participationWebsocketService, 'getParticipationsForExercise');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should display the score', () => {
        setupExercise(ExerciseType.PROGRAMMING, dayjs());

        const studentParticipation = {
            id: 1,
            initializationState: InitializationState.INITIALIZED,
            testRun: false,
            results: [{ rated: true, score: 42 } as Result],
        } as StudentParticipation;
        comp.exercise.studentParticipations = [studentParticipation];

        getAllParticipationsStub.mockReturnValue([studentParticipation]);
        comp.ngOnChanges();
        comp.ngOnInit();

        fixture.detectChanges();

        const result = fixture.debugElement.query(By.css('jhi-submission-result-status'));
        expect(result).not.toBeNull();
    });

    const setupExercise = (exerciseType: ExerciseType, dueDate: dayjs.Dayjs) => {
        comp.exercise = {
            id: 1,
            type: exerciseType,
            dueDate,
        } as Exercise;
    };
});
