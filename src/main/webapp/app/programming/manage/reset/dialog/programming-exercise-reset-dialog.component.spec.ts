import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { ProgrammingExerciseResetDialogComponent } from 'app/programming/manage/reset/dialog/programming-exercise-reset-dialog.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ProgrammingExerciseResetOptions, ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockProvider } from 'ng-mocks';

describe('ProgrammingExerciseResetDialogComponent', () => {
    let comp: ProgrammingExerciseResetDialogComponent;
    let fixture: ComponentFixture<ProgrammingExerciseResetDialogComponent>;
    let programmingExerciseService: ProgrammingExerciseService;

    const exerciseId = 42;
    const programmingExercise = new ProgrammingExercise(new Course(), undefined);
    programmingExercise.id = exerciseId;
    programmingExercise.title = 'Programming Exercise';
    programmingExercise.releaseDate = dayjs();
    programmingExercise.dueDate = dayjs().add(7, 'days');

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                LocalStorageService,
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(NgbActiveModal),
                provideHttpClient(),
            ],
        }).compileComponents();
        // Ignore console errors
        console.error = () => false;
        fixture = TestBed.createComponent(ProgrammingExerciseResetDialogComponent);
        comp = fixture.componentInstance;
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);

        // stubs
        jest.spyOn(programmingExerciseService, 'find').mockReturnValue(of({ body: programmingExercise } as HttpResponse<ProgrammingExercise>));

        comp.programmingExercise = programmingExercise;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should close the modal dialog', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        jest.spyOn(activeModal, 'dismiss').mockImplementation();

        comp.clear();

        expect(activeModal.dismiss).toHaveBeenCalledWith('cancel');
    });

    it('resetProgrammingExercise should make the correct service call and call handleResetResponse()', () => {
        const resetResponse = of('');
        jest.spyOn(programmingExerciseService, 'reset').mockReturnValue(resetResponse);
        jest.spyOn(comp, 'handleResetResponse').mockImplementation();

        comp.programmingExercise.id = exerciseId;
        comp.programmingExerciseResetOptions = {
            deleteParticipationsSubmissionsAndResults: true,
            recreateBuildPlans: true,
        };
        comp.resetProgrammingExercise();

        expect(programmingExerciseService.reset).toHaveBeenCalledWith(exerciseId, comp.programmingExerciseResetOptions);
        expect(comp.handleResetResponse).toHaveBeenCalled();
    });

    describe('handleResetResponse', () => {
        it('should show the correct success message and dismiss the active modal', () => {
            const activeModal = TestBed.inject(NgbActiveModal);
            const alertService = TestBed.inject(AlertService);
            jest.spyOn(activeModal, 'dismiss').mockImplementation();
            jest.spyOn(alertService, 'success').mockImplementation();

            comp.handleResetResponse();

            expect(alertService.success).toHaveBeenCalledWith('artemisApp.programmingExercise.reset.successMessage');
            expect(activeModal.dismiss).toHaveBeenCalledWith(true);
        });

        it('should not be called when there is an error in the reset response', fakeAsync(() => {
            const errorResponse = throwError(
                () =>
                    new HttpErrorResponse({
                        status: 500,
                    }),
            );
            comp.programmingExerciseResetOptions = {
                deleteParticipationsSubmissionsAndResults: true,
                recreateBuildPlans: false,
            };
            jest.spyOn(programmingExerciseService, 'reset').mockReturnValue(errorResponse);
            jest.spyOn(comp, 'handleResetResponse').mockImplementation();

            comp.resetProgrammingExercise();

            tick();

            expect(comp.handleResetResponse).not.toHaveBeenCalled();
            expect(comp.resetInProgress).toBeFalse();
        }));
    });

    describe('canSubmit', () => {
        beforeEach(() => {
            comp.confirmText = 'Programming Exercise';
            comp.resetInProgress = false;
            comp.programmingExerciseResetOptions = {
                deleteParticipationsSubmissionsAndResults: true,
                recreateBuildPlans: false,
            };
        });

        it('should return true when confirmation text is filled correctly and at least one option is selected', () => {
            expect(comp.canSubmit).toBeTrue();
        });

        it('should return false when confirmation text is empty', () => {
            comp.confirmText = '';
            expect(comp.canSubmit).toBeFalse();
        });

        it('should return false when confirmation text is not filled correctly', () => {
            comp.confirmText = 'Incorrect Name';
            expect(comp.canSubmit).toBeFalse();
        });

        it('should return false when confirmation text is filled correctly, but no option is selected', () => {
            comp.programmingExerciseResetOptions.deleteParticipationsSubmissionsAndResults = false;
            expect(comp.canSubmit).toBeFalse();
        });

        it('should return false when reset is in progress', () => {
            comp.resetInProgress = true;
            expect(comp.canSubmit).toBeFalse();
        });
    });

    describe('hasSelectedOptions', () => {
        it('should return false when all options are set to false', () => {
            comp.programmingExerciseResetOptions = {
                deleteParticipationsSubmissionsAndResults: false,
                recreateBuildPlans: false,
            };

            expect(comp.hasSelectedOptions).toBeFalse();
        });

        it.each`
            option
            ${'deleteParticipationsSubmissionsAndResults'}
            ${'recreateBuildPlans'}
        `('should return true when $option is set to true', ({ option }) => {
            comp.programmingExerciseResetOptions = {
                deleteParticipationsSubmissionsAndResults: false,
                recreateBuildPlans: false,
            };
            comp.programmingExerciseResetOptions[option as keyof ProgrammingExerciseResetOptions] = true;
            expect(comp.hasSelectedOptions).toBeTrue();
        });
    });
});
