import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { Course } from 'app/entities/course.model';
import { MockDirective, MockPipe } from 'ng-mocks';
import { NgModel } from '@angular/forms';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-trigger-build-button.component';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-button.component';
import { of } from 'rxjs';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { SubmissionType } from 'app/entities/submission.model';

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
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseInstructorTriggerBuildButtonComponent, ButtonComponent, MockPipe(ArtemisTranslatePipe), MockDirective(NgModel)],
            providers: [
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
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
