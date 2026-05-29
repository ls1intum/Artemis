import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/instructor/programming-exercise-instructor-trigger-build-button.component';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmAutofocusModalComponent } from 'app/shared-ui/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { of } from 'rxjs';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';

describe('ProgrammingExercise Instructor Trigger Build Component', () => {
    setupTestBed({ zoneless: true });

    const course = { id: 123 } as Course;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.id = 456;
    programmingExercise.title = 'Exercise 1';
    const participation = new StudentParticipation(ParticipationType.PROGRAMMING);
    participation.id = 567;

    let comp: ProgrammingExerciseInstructorTriggerBuildButtonComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructorTriggerBuildButtonComponent>;
    let submissionService: ProgrammingSubmissionService;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: NgbModal, useClass: MockNgbModalService },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseInstructorTriggerBuildButtonComponent);
        comp = fixture.componentInstance;
        submissionService = TestBed.inject(ProgrammingSubmissionService);
        modalService = TestBed.inject(NgbModal);

        fixture.componentRef.setInput('exercise', programmingExercise);
        fixture.componentRef.setInput('participation', participation);
        comp.lastResultIsManual.set(true);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should trigger build', async () => {
        const mockReturnValue = {
            result: Promise.resolve(),
            componentInstance: {},
        } as NgbModalRef;
        vi.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);
        vi.spyOn(submissionService, 'triggerBuild').mockReturnValue(of());

        comp.triggerBuild({ stopPropagation: vi.fn() } as unknown as MouseEvent);

        expect(modalService.open).toHaveBeenCalledOnce();
        expect(modalService.open).toHaveBeenCalledWith(ConfirmAutofocusModalComponent, {
            size: 'lg',
            keyboard: true,
        });

        // Allow the modal's `result` promise to resolve so the trigger callback runs.
        await Promise.resolve();
        await Promise.resolve();

        expect(submissionService.triggerBuild).toHaveBeenCalledOnce();
        expect(submissionService.triggerBuild).toHaveBeenCalledWith(participation.id, SubmissionType.INSTRUCTOR);
    });
});
