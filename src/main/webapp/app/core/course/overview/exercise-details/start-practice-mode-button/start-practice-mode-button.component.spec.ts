import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/shared/service/alert.service';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ExerciseActionButtonComponent } from 'app/shared/components/buttons/exercise-action-button/exercise-action-button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { Subject } from 'rxjs';
import dayjs from 'dayjs/esm';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClient } from '@angular/common/http';
import { StartPracticeModeButtonComponent } from 'app/core/course/overview/exercise-details/start-practice-mode-button/start-practice-mode-button.component';

describe('JhiStartPracticeModeButtonComponent', () => {
    let comp: StartPracticeModeButtonComponent;
    let fixture: ComponentFixture<StartPracticeModeButtonComponent>;

    let courseExerciseService: CourseExerciseService;
    let startPracticeStub: jest.SpyInstance;
    let alertService: AlertService;
    let alertServiceSuccessStub: jest.SpyInstance;
    let alertServiceErrorStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockDirective(NgbPopover)],
            declarations: [StartPracticeModeButtonComponent, MockComponent(ExerciseActionButtonComponent), MockPipe(ArtemisTranslatePipe), MockDirective(FeatureToggleDirective)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, { provide: AccountService, useClass: MockAccountService }, provideHttpClient()],
        }).compileComponents();

        fixture = TestBed.createComponent(StartPracticeModeButtonComponent);
        comp = fixture.componentInstance;
        courseExerciseService = TestBed.inject(CourseExerciseService);
        alertService = TestBed.inject(AlertService);

        startPracticeStub = jest.spyOn(courseExerciseService, 'startPractice');
        alertServiceSuccessStub = jest.spyOn(alertService, 'success');
        alertServiceErrorStub = jest.spyOn(alertService, 'error');
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should reflect the correct participation state for practice mode with no graded participation', fakeAsync(() => {
        const exercise = {
            id: 43,
            type: ExerciseType.PROGRAMMING,
            dueDate: dayjs().subtract(5, 'minutes'),
            studentParticipations: [] as StudentParticipation[],
        } as ProgrammingExercise;
        const inactivePart = { id: 2, initializationState: InitializationState.UNINITIALIZED, testRun: true } as StudentParticipation;
        const initPart = { id: 2, initializationState: InitializationState.INITIALIZED, testRun: true } as StudentParticipation;
        const participationSubject = new Subject<StudentParticipation>();

        comp.exercise = exercise;

        fixture.changeDetectorRef.detectChanges();
        tick();

        startPracticeStub.mockReturnValue(participationSubject);
        comp.startPractice(false);
        participationSubject.next(inactivePart);

        fixture.changeDetectorRef.detectChanges();
        tick();

        expect(alertServiceErrorStub).toHaveBeenCalledOnce();
        expect(alertServiceErrorStub).toHaveBeenCalledWith('artemisApp.exercise.startError');
        expect(startPracticeStub).toHaveBeenCalledOnce();

        comp.exercise.studentParticipations = [];
        participationSubject.next(initPart);

        fixture.changeDetectorRef.detectChanges();
        tick();

        expect(alertServiceSuccessStub).toHaveBeenCalledOnce();
        expect(alertServiceSuccessStub).toHaveBeenCalledWith('artemisApp.exercise.personalRepositoryOnline');

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

        fixture.changeDetectorRef.detectChanges();
        tick();

        startPracticeStub.mockReturnValue(participationSubject);
        comp.startPractice(false);
        participationSubject.next(inactivePart);

        fixture.changeDetectorRef.detectChanges();
        tick();

        expect(alertServiceErrorStub).toHaveBeenCalledOnce();
        expect(alertServiceErrorStub).toHaveBeenCalledWith('artemisApp.exercise.startError');
        expect(startPracticeStub).toHaveBeenCalledOnce();

        participationSubject.next(initPart);

        fixture.changeDetectorRef.detectChanges();
        tick();

        expect(alertServiceSuccessStub).toHaveBeenCalledOnce();
        expect(alertServiceSuccessStub).toHaveBeenCalledWith('artemisApp.exercise.personalRepositoryClone');

        fixture.destroy();
        flush();
    }));
});
