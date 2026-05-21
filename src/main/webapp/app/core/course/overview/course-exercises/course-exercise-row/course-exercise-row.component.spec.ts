import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { By } from '@angular/platform-browser';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockCourseExerciseService } from 'test/helpers/mocks/service/mock-course-exercise.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import dayjs from 'dayjs/esm';
import { MockCourseService } from 'test/helpers/mocks/service/mock-course.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockComponent, MockPipe } from 'ng-mocks';
import { NotReleasedTagComponent } from 'app/shared/components/not-released-tag/not-released-tag.component';
import { DifficultyBadgeComponent } from 'app/exercise/exercise-headers/difficulty-badge/difficulty-badge.component';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { RouterModule } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { SubmissionResultStatusComponent } from 'app/core/course/overview/submission-result-status/submission-result-status.component';
import { ExerciseDetailsStudentActionsComponent } from 'app/core/course/overview/exercise-details/student-actions/exercise-details-student-actions.component';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';
import { CourseExerciseRowComponent } from 'app/core/course/overview/course-exercises/course-exercise-row/course-exercise-row.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

@Component({
    template: '',
})
class DummyComponent {}

describe('CourseExerciseRowComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CourseExerciseRowComponent>;
    let getAllParticipationsStub: ReturnType<typeof vi.spyOn>;
    let participationWebsocketService: ParticipationWebsocketService;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [
                TranslateModule.forRoot(),
                RouterModule.forRoot([
                    { path: 'courses/:courseId/exercises', component: DummyComponent },
                    { path: 'courses/:courseId/exercises/:exerciseId', component: DummyComponent },
                ]),
                NgbModule,
                FaIconComponent,
                MockComponent(SubmissionResultStatusComponent),
                MockComponent(ExerciseDetailsStudentActionsComponent),
                MockComponent(NotReleasedTagComponent),
                MockComponent(DifficultyBadgeComponent),
                MockComponent(ExerciseCategoriesComponent),
                MockComponent(IncludedInScoreBadgeComponent),
                MockPipe(ArtemisTimeAgoPipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTranslatePipe),
                CourseExerciseRowComponent,
                DummyComponent,
            ],
            providers: [
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: CourseManagementService, useClass: MockCourseService },
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                SessionStorageService,
                LocalStorageService,
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });
        TestBed.overrideComponent(CourseExerciseRowComponent, {
            remove: {
                imports: [
                    ArtemisTimeAgoPipe,
                    ArtemisDatePipe,
                    ArtemisTranslatePipe,
                    SubmissionResultStatusComponent,
                    ExerciseDetailsStudentActionsComponent,
                    ExerciseCategoriesComponent,
                ],
            },
            add: {
                imports: [
                    MockPipe(ArtemisTimeAgoPipe),
                    MockPipe(ArtemisDatePipe),
                    MockPipe(ArtemisTranslatePipe),
                    MockComponent(SubmissionResultStatusComponent),
                    MockComponent(ExerciseDetailsStudentActionsComponent),
                    MockComponent(ExerciseCategoriesComponent),
                ],
            },
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseExerciseRowComponent);
        fixture.componentRef.setInput('course', { id: 123, isAtLeastInstructor: true } as Course);
        participationWebsocketService = TestBed.inject(ParticipationWebsocketService);
        getAllParticipationsStub = vi.spyOn(participationWebsocketService, 'getParticipationsForExercise');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should display the score', async () => {
        const studentParticipation = {
            id: 1,
            initializationState: InitializationState.INITIALIZED,
            testRun: false,
            results: [{ rated: true, score: 42 } as Result],
        } as StudentParticipation;

        getAllParticipationsStub.mockReturnValue([studentParticipation]);

        const exercise = {
            id: 1,
            type: ExerciseType.PROGRAMMING,
            dueDate: dayjs(),
            studentParticipations: [studentParticipation],
        } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);

        TestBed.tick();
        fixture.detectChanges();
        await fixture.whenStable();

        const result = fixture.debugElement.query(By.css('jhi-submission-result-status'));
        expect(result).not.toBeNull();
    });
});
