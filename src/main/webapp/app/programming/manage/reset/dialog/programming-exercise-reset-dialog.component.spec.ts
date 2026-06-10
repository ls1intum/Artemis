import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { ProgrammingExerciseResetDialogComponent } from 'app/programming/manage/reset/dialog/programming-exercise-reset-dialog.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExerciseResetOptions, ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('ProgrammingExerciseResetDialogComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: ProgrammingExerciseResetDialogComponent;
    let fixture: ComponentFixture<ProgrammingExerciseResetDialogComponent>;
    let programmingExerciseService: ProgrammingExerciseService;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;

    const exerciseId = 42;
    const programmingExercise = new ProgrammingExercise(new Course(), undefined);
    programmingExercise.id = exerciseId;
    programmingExercise.title = 'Programming Exercise';
    programmingExercise.releaseDate = dayjs();
    programmingExercise.dueDate = dayjs().add(7, 'days');

    beforeEach(async () => {
        dialogRefCloseSpy = vi.fn();

        await TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                LocalStorageService,
                { provide: ProfileService, useClass: MockProfileService },
                { provide: DynamicDialogRef, useValue: { close: dialogRefCloseSpy } },
                { provide: DynamicDialogConfig, useValue: { data: { programmingExercise } } },
                provideHttpClient(),
            ],
        }).compileComponents();
        // Ignore console errors
        console.error = () => false;
        fixture = TestBed.createComponent(ProgrammingExerciseResetDialogComponent);
        comp = fixture.componentInstance;
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);

        // stubs
        vi.spyOn(programmingExerciseService, 'find').mockReturnValue(of({ body: programmingExercise } as HttpResponse<ProgrammingExercise>));

        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should read the programming exercise from the dialog config data', () => {
        expect(comp.programmingExercise).toBe(programmingExercise);
    });

    it('should close the dialog', () => {
        comp.clear();

        expect(dialogRefCloseSpy).toHaveBeenCalledWith('cancel');
    });

    it('resetProgrammingExercise should make the correct service call and call handleResetResponse()', () => {
        const resetResponse = of('');
        vi.spyOn(programmingExerciseService, 'reset').mockReturnValue(resetResponse);
        vi.spyOn(comp, 'handleResetResponse').mockImplementation(() => {});

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
        it('should show the correct success message and close the dialog', () => {
            const alertService = TestBed.inject(AlertService);
            vi.spyOn(alertService, 'success').mockImplementation(() => undefined as any);

            comp.handleResetResponse();

            expect(alertService.success).toHaveBeenCalledWith('artemisApp.programmingExercise.reset.successMessage');
            expect(dialogRefCloseSpy).toHaveBeenCalledWith(true);
        });

        it('should not be called when there is an error in the reset response', () => {
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
            vi.spyOn(programmingExerciseService, 'reset').mockReturnValue(errorResponse);
            vi.spyOn(comp, 'handleResetResponse').mockImplementation(() => {});

            comp.resetProgrammingExercise();

            expect(comp.handleResetResponse).not.toHaveBeenCalled();
            expect(comp.resetInProgress).toBe(false);
        });
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
            expect(comp.canSubmit).toBe(true);
        });

        it('should return false when confirmation text is empty', () => {
            comp.confirmText = '';
            expect(comp.canSubmit).toBe(false);
        });

        it('should return false when confirmation text is not filled correctly', () => {
            comp.confirmText = 'Incorrect Name';
            expect(comp.canSubmit).toBe(false);
        });

        it('should return false when confirmation text is filled correctly, but no option is selected', () => {
            comp.programmingExerciseResetOptions.deleteParticipationsSubmissionsAndResults = false;
            expect(comp.canSubmit).toBe(false);
        });

        it('should return false when reset is in progress', () => {
            comp.resetInProgress = true;
            expect(comp.canSubmit).toBe(false);
        });
    });

    describe('hasSelectedOptions', () => {
        it('should return false when all options are set to false', () => {
            comp.programmingExerciseResetOptions = {
                deleteParticipationsSubmissionsAndResults: false,
                recreateBuildPlans: false,
            };

            expect(comp.hasSelectedOptions).toBe(false);
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
            expect(comp.hasSelectedOptions).toBe(true);
        });
    });
});
