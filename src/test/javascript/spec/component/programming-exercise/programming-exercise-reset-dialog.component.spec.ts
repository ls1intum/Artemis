import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseResetDialogComponent } from 'app/exercises/programming/manage/reset/programming-exercise-reset-dialog.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateValuesDirective } from '../../helpers/mocks/directive/mock-translate-values.directive';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { NgForm, NgModel } from '@angular/forms';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

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
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ProgrammingExerciseResetDialogComponent,
                MockTranslateValuesDirective,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FormDateTimePickerComponent),
                MockDirective(TranslateDirective),
                MockDirective(FeatureToggleDirective),
                MockDirective(NgModel),
                MockDirective(NgForm),
                MockDirective(MockHasAnyAuthorityDirective),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                // Ignore console errors
                console.error = () => false;
                fixture = TestBed.createComponent(ProgrammingExerciseResetDialogComponent);
                comp = fixture.componentInstance;
                programmingExerciseService = fixture.debugElement.injector.get(ProgrammingExerciseService);

                // stubs
                jest.spyOn(programmingExerciseService, 'find').mockReturnValue(of({ body: programmingExercise } as HttpResponse<ProgrammingExercise>));

                comp.programmingExercise = programmingExercise;
            });
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
            comp.programmingExerciseResetOptions[option] = true;
            expect(comp.hasSelectedOptions).toBeTrue();
        });
    });
});
