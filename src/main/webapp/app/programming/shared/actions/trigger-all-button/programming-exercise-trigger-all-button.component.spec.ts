import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import {
    ProgrammingExerciseInstructorTriggerAllDialogComponent,
    ProgrammingExerciseTriggerAllButtonComponent,
} from 'app/programming/shared/actions/trigger-all-button/programming-exercise-trigger-all-button.component';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { BuildRunState, ProgrammingBuildRunService } from 'app/programming/shared/services/programming-build-run.service';
import { Subject, of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ProgrammingExercise Trigger All Button Component', () => {
    setupTestBed({ zoneless: true });

    const course = { id: 123 } as Course;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.id = 456;
    programmingExercise.title = 'Exercise 1';

    let fixture: ComponentFixture<ProgrammingExerciseTriggerAllButtonComponent>;
    let submissionService: ProgrammingSubmissionService;
    let dialogService: DialogService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: DialogService, useClass: MockDialogService },
                LocalStorageService,
                SessionStorageService,
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseTriggerAllButtonComponent);
        submissionService = TestBed.inject(ProgrammingSubmissionService);
        dialogService = TestBed.inject(DialogService);
        const buildRunService = TestBed.inject(ProgrammingBuildRunService);
        vi.spyOn(buildRunService, 'getBuildRunUpdates').mockReturnValue(new Subject<BuildRunState>());

        fixture.componentRef.setInput('exercise', programmingExercise);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should trigger builds for all participants on confirmation', () => {
        const onClose = new Subject<boolean | undefined>();
        const mockDialogRef = { onClose } as any;
        const openSpy = vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
        const triggerSpy = vi.spyOn(submissionService, 'triggerInstructorBuildForAllParticipationsOfExercise').mockReturnValue(of());

        fixture.detectChanges();
        const button = fixture.debugElement.nativeElement.querySelector('#trigger-all-button button');
        button.click();

        expect(openSpy).toHaveBeenCalledOnce();
        const [dialogComponent, options] = openSpy.mock.calls[0];
        expect(dialogComponent).toBe(ProgrammingExerciseInstructorTriggerAllDialogComponent);
        expect(options).toMatchObject({
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            data: { exerciseId: programmingExercise.id, dueDatePassed: false },
        });

        // Simulate the user confirming the dialog.
        onClose.next(true);

        expect(triggerSpy).toHaveBeenCalledOnce();
        expect(triggerSpy).toHaveBeenCalledWith(programmingExercise.id);
    });

    it('should not trigger any build if the dialog is dismissed', () => {
        const onClose = new Subject<boolean | undefined>();
        const mockDialogRef = { onClose } as any;
        vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
        const triggerSpy = vi.spyOn(submissionService, 'triggerInstructorBuildForAllParticipationsOfExercise').mockReturnValue(of());

        fixture.detectChanges();
        const button = fixture.debugElement.nativeElement.querySelector('#trigger-all-button button');
        button.click();

        // Dismiss (undefined) — must NOT trigger the build.
        onClose.next(undefined);

        expect(triggerSpy).not.toHaveBeenCalled();
    });
});
