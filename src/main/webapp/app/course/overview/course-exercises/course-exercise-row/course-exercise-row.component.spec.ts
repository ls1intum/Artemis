import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { By } from '@angular/platform-browser';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { provideTranslateService } from '@ngx-translate/core';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockCourseExerciseService } from 'test/helpers/mocks/service/mock-course-exercise.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import dayjs from 'dayjs/esm';
import { BehaviorSubject } from 'rxjs';
import { MockCourseService } from 'test/helpers/mocks/service/mock-course.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { InitializationState, Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Course } from 'app/course/shared/entities/course.model';
import { MockComponent, MockPipe } from 'ng-mocks';
import { NotReleasedTagComponent } from 'app/shared-ui/components/not-released-tag/not-released-tag.component';
import { DifficultyBadgeComponent } from 'app/exercise/exercise-headers/difficulty-badge/difficulty-badge.component';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { RouterModule } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { SubmissionResultStatusComponent } from 'app/course/overview/submission-result-status/submission-result-status.component';
import { ExerciseDetailsStudentActionsComponent } from 'app/course/overview/exercise-details/student-actions/exercise-details-student-actions.component';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';
import { CourseExerciseRowComponent } from 'app/course/overview/course-exercises/course-exercise-row/course-exercise-row.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

@Component({
    template: '',
})
class DummyComponent {}

describe('CourseExerciseRowComponent', () => {
    let fixture: ComponentFixture<CourseExerciseRowComponent>;
    let getAllParticipationsStub: ReturnType<typeof vi.spyOn>;
    let participationWebsocketService: ParticipationWebsocketService;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [
                RouterModule.forRoot([
                    { path: 'courses/:courseId/exercises', component: DummyComponent },
                    { path: 'courses/:courseId/exercises/:exerciseId', component: DummyComponent },
                ]),
                NgbTooltip,
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
                provideTranslateService(),
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
    it('should refresh the enriched exercise when a participation arrives over the websocket', async () => {
        const participationChanges = new BehaviorSubject<Participation | undefined>(undefined);
        vi.spyOn(participationWebsocketService, 'subscribeForParticipationChanges').mockReturnValue(participationChanges);
        getAllParticipationsStub.mockReturnValue([]);
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.TEXT } as Exercise);
        TestBed.tick();
        fixture.detectChanges();
        await fixture.whenStable();

        const updated = { id: 7, testRun: false, exercise: { id: 1 } as Exercise } as StudentParticipation;
        participationChanges.next(updated);
        TestBed.tick();
        fixture.detectChanges();

        expect(fixture.componentInstance.enrichedExercise().studentParticipations).toEqual([updated]);
    });

    it('should keep the course shared instead of copying the course graph on every websocket update', async () => {
        // Regression guard: the row's exercise carries `course`, and the stored course reaches every other exercise of
        // the course. Copying the enriched exercise therefore cloned the whole graph once per event and handed this row
        // a course detached from the one the rest of the page shares.
        const participationChanges = new BehaviorSubject<Participation | undefined>(undefined);
        vi.spyOn(participationWebsocketService, 'subscribeForParticipationChanges').mockReturnValue(participationChanges);
        getAllParticipationsStub.mockReturnValue([]);
        const course = { id: 123, isAtLeastInstructor: true, exercises: [{ id: 1 }, { id: 2 }] } as Course;
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.TEXT } as Exercise);
        TestBed.tick();
        fixture.detectChanges();
        await fixture.whenStable();

        participationChanges.next({ id: 7, testRun: false, exercise: { id: 1 } as Exercise } as StudentParticipation);
        TestBed.tick();
        fixture.detectChanges();

        expect(fixture.componentInstance.enrichedExercise().course).toBe(course);
    });
});
