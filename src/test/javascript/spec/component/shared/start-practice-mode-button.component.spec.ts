import { ComponentFixture, TestBed, fakeAsync, tick, flush } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { NgbPopoverModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTestModule } from '../../test.module';
import { MockFeatureToggleService } from '../../helpers/mocks/service/mock-feature-toggle.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { StartPracticeModeButtonComponent } from 'app/shared/components/start-practice-mode-button/start-practice-mode-button.component';
import { ExerciseType, ParticipationStatus } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { InitializationState } from 'app/entities/participation/participation.model';
import { Subject } from 'rxjs';
import dayjs from 'dayjs/esm';
import { participationStatus } from 'app/exercises/shared/exercise/exercise.utils';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { MockCourseExerciseService } from '../../helpers/mocks/service/mock-course-exercise.service';

describe('JhiStartPracticeModeButtonComponent', () => {
    let comp: StartPracticeModeButtonComponent;
    let fixture: ComponentFixture<StartPracticeModeButtonComponent>;

    let courseExerciseService: CourseExerciseService;
    let startPracticeStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbPopoverModule],
            declarations: [StartPracticeModeButtonComponent, MockComponent(ExerciseActionButtonComponent), MockPipe(ArtemisTranslatePipe), MockDirective(FeatureToggleDirective)],
            providers: [
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(StartPracticeModeButtonComponent);
        comp = fixture.componentInstance;
        courseExerciseService = fixture.debugElement.injector.get(CourseExerciseService);

        startPracticeStub = jest.spyOn(courseExerciseService, 'startPractice');
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should reflect the correct participation state for practice mode with no graded participation', fakeAsync(() => {
        const exercise = {
            id: 43,
            type: ExerciseType.PROGRAMMING,
            dueDate: dayjs().subtract(5, 'minutes'),
            allowOfflineIde: true,
            studentParticipations: [] as StudentParticipation[],
        } as ProgrammingExercise;
        const inactivePart = { id: 2, initializationState: InitializationState.UNINITIALIZED, testRun: true } as StudentParticipation;
        const initPart = { id: 2, initializationState: InitializationState.INITIALIZED, testRun: true } as StudentParticipation;
        const participationSubject = new Subject<StudentParticipation>();

        comp.exercise = exercise;

        fixture.detectChanges();
        tick();

        startPracticeStub.mockReturnValue(participationSubject);
        comp.startPractice(false);
        participationSubject.next(inactivePart);

        fixture.detectChanges();
        tick();

        expect(participationStatus(exercise, true)).toEqual(ParticipationStatus.UNINITIALIZED);
        expect(comp.exercise.studentParticipations).toEqual([inactivePart]);
        expect(startPracticeStub).toHaveBeenCalledOnce();

        comp.exercise.studentParticipations = [];
        participationSubject.next(initPart);

        fixture.detectChanges();
        tick();

        expect(participationStatus(exercise, true)).toEqual(ParticipationStatus.INITIALIZED);
        expect(comp.exercise.studentParticipations).toEqual([initPart]);

        fixture.destroy();
        flush();
    }));

    it('should reflect the correct participation state for practice mode with graded participation', fakeAsync(() => {
        const exercise = {
            id: 43,
            type: ExerciseType.PROGRAMMING,
            dueDate: dayjs().subtract(5, 'minutes'),
            allowOfflineIde: true,
            studentParticipations: [] as StudentParticipation[],
        } as ProgrammingExercise;
        const gradedPart = { id: 1, initializationState: InitializationState.FINISHED, testRun: false } as StudentParticipation;
        const inactivePart = { id: 2, initializationState: InitializationState.UNINITIALIZED, testRun: true } as StudentParticipation;
        const initPart = { id: 2, initializationState: InitializationState.INITIALIZED, testRun: true } as StudentParticipation;
        const participationSubject = new Subject<StudentParticipation>();

        comp.exercise = exercise;
        comp.exercise.studentParticipations = [gradedPart];

        fixture.detectChanges();
        tick();

        startPracticeStub.mockReturnValue(participationSubject);
        comp.startPractice(false);
        participationSubject.next(inactivePart);

        fixture.detectChanges();
        tick();

        expect(participationStatus(exercise, true)).toEqual(ParticipationStatus.UNINITIALIZED);
        expect(comp.exercise.studentParticipations).toEqual([gradedPart, inactivePart]);
        expect(startPracticeStub).toHaveBeenCalledOnce();

        participationSubject.next(initPart);

        fixture.detectChanges();
        tick();

        expect(participationStatus(exercise, true)).toEqual(ParticipationStatus.INITIALIZED);
        expect(comp.exercise.studentParticipations).toEqual([gradedPart, initPart]);

        fixture.destroy();
        flush();
    }));
});
