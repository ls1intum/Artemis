import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TextExamSummaryComponent } from 'app/exam/overview/summary/exercises/text-exam-summary/text-exam-summary.component';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextEditorComponent } from 'app/text/overview/text-editor/text-editor.component';
import { By } from '@angular/platform-browser';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { AccountService } from 'app/core/auth/account.service';

describe('TextExamSummaryComponent', () => {
    let fixture: ComponentFixture<TextExamSummaryComponent>;
    let component: TextExamSummaryComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ id: 123 }),
                },
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                SessionStorageService,
                {
                    provide: ProfileService,
                    useClass: MockProfileService,
                },
                {
                    provide: AccountService,
                    useValue: { isOwnerOfParticipation: jest.fn().mockReturnValue(true) },
                },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextExamSummaryComponent);
                component = fixture.componentInstance;
            });
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(fixture.debugElement.nativeElement.querySelector('div').innerHTML).toContain('No submission');
    });

    it('should display the submission text', () => {
        const submissionText = 'A test submission text';
        const exercise = { studentParticipations: [{ id: 1 }] } as Exercise;
        exercise.studentParticipations![0].exercise = exercise;
        component.submission = { text: submissionText } as TextSubmission;
        component.exercise = exercise;
        fixture.detectChanges();

        const textEditorComponent = fixture.debugElement.query(By.directive(TextEditorComponent)).componentInstance;
        expect(textEditorComponent).not.toBeNull();
        expect(textEditorComponent.participationId()).toBe(1);
        expect(textEditorComponent.inputSubmission().text).toBe(submissionText);
    });
});
