import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { Course } from 'app/entities/course.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import {
    ProgrammingExerciseInstructorTriggerAllDialogComponent,
    ProgrammingExerciseTriggerAllButtonComponent,
} from 'app/exercises/programming/shared/actions/programming-exercise-trigger-all-button.component';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { of } from 'rxjs';
import { MockDirective, MockPipe } from 'ng-mocks';
import { NgModel } from '@angular/forms';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('ProgrammingExercise Trigger All Button Component', () => {
    const course = { id: 123 } as Course;
    const programmingExercise = new ProgrammingExercise(course, undefined);
    programmingExercise.id = 456;
    programmingExercise.title = 'Exercise 1';

    let comp: ProgrammingExerciseTriggerAllButtonComponent;
    let fixture: ComponentFixture<ProgrammingExerciseTriggerAllButtonComponent>;
    let submissionService: ProgrammingSubmissionService;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseTriggerAllButtonComponent, ButtonComponent, MockPipe(ArtemisTranslatePipe), MockDirective(NgModel)],
            providers: [
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseTriggerAllButtonComponent);
        comp = fixture.componentInstance;
        submissionService = fixture.debugElement.injector.get(ProgrammingSubmissionService);
        modalService = fixture.debugElement.injector.get(NgbModal);

        comp.exercise = programmingExercise;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should trigger builds for all participants on confirmation', () => {
        const mockReturnValue = {
            result: Promise.resolve(),
            componentInstance: {},
        } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);
        jest.spyOn(submissionService, 'triggerInstructorBuildForAllParticipationsOfExercise').mockReturnValue(of());

        const button = fixture.debugElement.nativeElement.querySelector('#trigger-all-button button');
        button.click();

        expect(modalService.open).toHaveBeenCalledOnce();
        expect(modalService.open).toHaveBeenCalledWith(ProgrammingExerciseInstructorTriggerAllDialogComponent, {
            size: 'lg',
            backdrop: 'static',
        });

        expect(submissionService.triggerInstructorBuildForAllParticipationsOfExercise).toHaveBeenCalledOnce();
        expect(submissionService.triggerInstructorBuildForAllParticipationsOfExercise).toHaveBeenCalledWith(programmingExercise.id);
    });
});
