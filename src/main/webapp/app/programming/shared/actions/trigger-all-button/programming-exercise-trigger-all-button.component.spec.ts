import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockSyncStorage } from 'test/helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { Course } from 'app/core/course/shared/entities/course.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import {
    ProgrammingExerciseInstructorTriggerAllDialogComponent,
    ProgrammingExerciseTriggerAllButtonComponent,
} from 'app/programming/shared/actions/trigger-all-button/programming-exercise-trigger-all-button.component';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

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
            providers: [
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseTriggerAllButtonComponent);
        comp = fixture.componentInstance;
        submissionService = TestBed.inject(ProgrammingSubmissionService);
        modalService = TestBed.inject(NgbModal);

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
