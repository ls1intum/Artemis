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

    it('test initialization', () => {
        fixture.detectChanges();
        expect(comp.programmingExercise.id).toBe(42);
    });

    it('Programming exercise service should find the correct programming exercise', () => {
        fixture.detectChanges();
        expect(comp.programmingExercise).toEqual(programmingExercise);
    });

    it('clear() should close the modal dialog', () => {
        const activeModal = fixture.debugElement.injector.get(NgbActiveModal);
        jest.spyOn(activeModal, 'dismiss').mockImplementation();

        comp.clear();

        expect(activeModal.dismiss).toHaveBeenCalledWith('cancel');
    });

    describe('showUndeletedArtifactsWarning()', () => {
        it('should return true when deleteBuildPlans is false and deleteStudentRepositories is true', () => {
            comp.programmingExerciseResetOptions = {
                deleteBuildPlans: false,
                deleteRepositories: true,
                deleteParticipationsSubmissionsAndResults: false,
                recreateBuildPlans: false,
            };
            expect(comp.showUndeletedArtifactsWarning()).toBeTrue();
        });

        it('should return true when deleteBuildPlans is false and deleteStudentParticipationsSubmissionsAndResults is true', () => {
            comp.programmingExerciseResetOptions = {
                deleteBuildPlans: false,
                deleteRepositories: false,
                deleteParticipationsSubmissionsAndResults: true,
                recreateBuildPlans: false,
            };
            expect(comp.showUndeletedArtifactsWarning()).toBeTrue();
        });

        it('should return false when deleteBuildPlans and deleteStudentRepositories are true', () => {
            comp.programmingExerciseResetOptions = {
                deleteBuildPlans: true,
                deleteRepositories: true,
                deleteParticipationsSubmissionsAndResults: false,
                recreateBuildPlans: false,
            };
            expect(comp.showUndeletedArtifactsWarning()).toBeFalse();
        });

        it('should return false when all options are false', () => {
            comp.programmingExerciseResetOptions = {
                deleteBuildPlans: false,
                deleteRepositories: false,
                deleteParticipationsSubmissionsAndResults: false,
                recreateBuildPlans: false,
            };
            expect(comp.showUndeletedArtifactsWarning()).toBeFalse();
        });
    });

    it('resetProgrammingExercise() should make the correct service call and call handleResetResponse()', () => {
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

    describe('handleResetResponse()', () => {
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

    describe('areSecurityChecksFulfilled', () => {
        it('should return false when confirmation text is empty', () => {
            comp.confirmText = '';

            expect(comp.areSecurityChecksFulfilled).toBeFalse();
        });

        it('should return false when confirmation text is not filled correctly', () => {
            comp.confirmText = 'Incorrect Name';

            expect(comp.areSecurityChecksFulfilled).toBeFalse();
        });

        it('should return true when confirmation text is filled correctly', () => {
            comp.confirmText = 'Programming Exercise';

            expect(comp.areSecurityChecksFulfilled).toBeTrue();
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

        it('should return true when at least one option is set to true', () => {
            const options = {
                deleteBuildPlans: true,
                deleteStudentRepositories: true,
                deleteStudentParticipationsSubmissionsAndResults: true,
                recreateBuildPlans: true,
            };

            for (const option in options) {
                comp.programmingExerciseResetOptions = {
                    deleteBuildPlans: false,
                    deleteRepositories: false,
                    deleteParticipationsSubmissionsAndResults: false,
                    recreateBuildPlans: false,
                };
                comp.programmingExerciseResetOptions[option] = true;
                expect(comp.hasSelectedOptions).toBeTrue();
            }
        });
    });
});
