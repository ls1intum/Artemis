import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import dayjs from 'dayjs/esm';
import { ProgrammingExerciseResetDialogComponent } from 'app/programming/manage/reset/programming-exercise-reset-dialog.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { ProgrammingExerciseResetOptions, ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
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
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(NgbActiveModal),
                provideHttpClient(),
            ],
        }).compileComponents();
        // Ignore console errors
        console.error = () => false;
        fixture = TestBed.createComponent(ProgrammingExerciseResetDialogComponent);
        comp = fixture.componentInstance;
        programmingExerciseService = fixture.debugElement.injector.get(ProgrammingExerciseService);

        // stubs
        jest.spyOn(programmingExerciseService, 'find').mockReturnValue(of({ body: programmingExercise } as HttpResponse<ProgrammingExercise>));

        comp.programmingExercise = programmingExercise;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should close the modal dialog', () => {
        const activeModal = fixture.debugElement.injector.get(NgbActiveModal);
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
            deleteBuildPlans: true,
            deleteRepositories: true,
            deleteParticipationsSubmissionsAndResults: true,
            recreateBuildPlans: true,
        };
        comp.resetProgrammingExercise();

        expect(programmingExerciseService.reset).toHaveBeenCalledWith(exerciseId, comp.programmingExerciseResetOptions);
        expect(comp.handleResetResponse).toHaveBeenCalled();
    });

    describe('handleResetResponse', () => {
        it('should show the correct success message and dismiss the active modal', () => {
            const activeModal = fixture.debugElement.injector.get(NgbActiveModal);
            const alertService = fixture.debugElement.injector.get(AlertService);
            jest.spyOn(activeModal, 'dismiss').mockImplementation();
            jest.spyOn(alertService, 'success').mockImplementation();

            comp.handleResetResponse();

            expect(alertService.success).toHaveBeenCalledWith('artemisApp.programmingExercise.reset.successMessage');
            expect(activeModal.dismiss).toHaveBeenCalledWith(true);
        });

        it('should not be called when there is an error in the reset response', fakeAsync(() => {
            const errorResponse = throwError({ status: 500 });
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
                deleteBuildPlans: true,
                deleteRepositories: false,
                deleteParticipationsSubmissionsAndResults: false,
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
            comp.programmingExerciseResetOptions.deleteBuildPlans = false;
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
                deleteBuildPlans: false,
                deleteRepositories: false,
                deleteParticipationsSubmissionsAndResults: false,
                recreateBuildPlans: false,
            };

            expect(comp.hasSelectedOptions).toBeFalse();
        });

        it.each`
            option
            ${'deleteBuildPlans'}
            ${'deleteRepositories'}
            ${'deleteParticipationsSubmissionsAndResults'}
            ${'recreateBuildPlans'}
        `('should return true when $option is set to true', ({ option }) => {
            comp.programmingExerciseResetOptions = {
                deleteBuildPlans: false,
                deleteRepositories: false,
                deleteParticipationsSubmissionsAndResults: false,
                recreateBuildPlans: false,
            };
            comp.programmingExerciseResetOptions[option as keyof ProgrammingExerciseResetOptions] = true;
            expect(comp.hasSelectedOptions).toBeTrue();
        });
    });
});
