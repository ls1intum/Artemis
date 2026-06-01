import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/instructor/programming-exercise-instructor-trigger-build-button.component';
import { ConfirmAutofocusModalComponent } from 'app/shared-ui/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { Subject, of } from 'rxjs';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { DialogService } from 'primeng/dynamicdialog';

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
    let dialogService: DialogService;
    let dialogClose: Subject<any>;

    beforeEach(() => {
        dialogClose = new Subject<any>();
        TestBed.configureTestingModule({
            providers: [
                { provide: DialogService, useValue: { open: jest.fn(() => ({ onClose: dialogClose })) } },
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
        dialogService = TestBed.inject(DialogService);

        comp.exercise = programmingExercise;
        comp.participation = participation;
        comp.lastResultIsManual = true;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should trigger build', () => {
        jest.spyOn(dialogService, 'open');
        jest.spyOn(submissionService, 'triggerBuild').mockReturnValue(of());

        comp.triggerBuild({ stopPropagation: jest.fn() });

        expect(dialogService.open).toHaveBeenCalledOnce();
        expect(dialogService.open).toHaveBeenCalledWith(
            ConfirmAutofocusModalComponent,
            expect.objectContaining({
                width: '50rem',
                data: expect.objectContaining({
                    title: 'artemisApp.programmingExercise.resubmitSingle',
                }),
            }),
        );

        dialogClose.next({ confirmed: true });

        expect(submissionService.triggerBuild).toHaveBeenCalledOnce();
        expect(submissionService.triggerBuild).toHaveBeenCalledWith(participation.id, SubmissionType.INSTRUCTOR);
    });
});
