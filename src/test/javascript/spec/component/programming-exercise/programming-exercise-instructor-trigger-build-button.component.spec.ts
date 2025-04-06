import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/instructor/programming-exercise-instructor-trigger-build-button.component';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { of } from 'rxjs';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';

describe('ProgrammingExercise Instructor Trigger Build Component', () => {
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
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseInstructorTriggerBuildButtonComponent);
        comp = fixture.componentInstance;
        submissionService = fixture.debugElement.injector.get(ProgrammingSubmissionService);
        modalService = fixture.debugElement.injector.get(NgbModal);

        comp.exercise = programmingExercise;
        comp.participation = participation;
        comp.lastResultIsManual = true;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should trigger build', () => {
        const mockReturnValue = {
            result: Promise.resolve(),
            componentInstance: {},
        } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);
        jest.spyOn(submissionService, 'triggerBuild').mockReturnValue(of());

        comp.triggerBuild({ stopPropagation: jest.fn() });

        expect(modalService.open).toHaveBeenCalledOnce();
        expect(modalService.open).toHaveBeenCalledWith(ConfirmAutofocusModalComponent, {
            size: 'lg',
            keyboard: true,
        });

        expect(submissionService.triggerBuild).toHaveBeenCalledOnce();
        expect(submissionService.triggerBuild).toHaveBeenCalledWith(participation.id, SubmissionType.INSTRUCTOR);
    });
});
