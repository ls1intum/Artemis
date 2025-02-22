import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FileUploadExamSummaryComponent } from 'app/exam/participate/summary/exercises/file-upload-exam-summary/file-upload-exam-summary.component';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { By } from '@angular/platform-browser';
import { FileUploadSubmissionComponent } from 'app/exercises/file-upload/participate/file-upload-submission.component';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { MockActivatedRoute } from '../../../../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../../../helpers/mocks/service/mock-account.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from '../../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('FileUploadExamSummaryComponent', () => {
    let fixture: ComponentFixture<FileUploadExamSummaryComponent>;
    let component: FileUploadExamSummaryComponent;

    const fileUploadSubmission = { id: 1 } as FileUploadSubmission;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadExamSummaryComponent);
                component = fixture.componentInstance;
                component.submission = fileUploadSubmission;
            });
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should render submission when exercise and submisssion is set', () => {
        const exercise = { id: 1234, studentParticipations: [{ id: 1 }] } as FileUploadExercise;
        const submission = { submitted: true, filePath: 'filePath.pdf' } as FileUploadSubmission;
        component.submission = submission;
        component.exercise = exercise;

        fixture.detectChanges();

        const fileUploadSubmissionComponent = fixture.debugElement.query(By.directive(FileUploadSubmissionComponent)).componentInstance;
        expect(fileUploadSubmissionComponent).toBeTruthy();
    });

    it('should not render submission if exercise and submission are not set', () => {
        fixture.detectChanges();

        const fileUploadSubmissionComponent = fixture.debugElement.query(By.directive(FileUploadSubmissionComponent))?.componentInstance;
        expect(fileUploadSubmissionComponent).not.toBeTruthy();
    });
});
