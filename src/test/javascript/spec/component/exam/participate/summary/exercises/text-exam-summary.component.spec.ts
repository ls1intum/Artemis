import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TextExamSummaryComponent } from 'app/exam/overview/summary/exercises/text-exam-summary/text-exam-summary.component';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextEditorComponent } from 'app/text/overview/text-editor.component';
import { By } from '@angular/platform-browser';
import { MockActivatedRoute } from '../../../../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../../../helpers/mocks/service/mock-translate.service';
import { SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../../../helpers/mocks/service/mock-sync-storage.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from '../../../../../helpers/mocks/service/mock-profile.service';

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
                {
                    provide: SessionStorageService,
                    useClass: MockSyncStorage,
                },
                {
                    provide: ProfileService,
                    useClass: MockProfileService,
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
        component.submission = { text: submissionText } as TextSubmission;
        component.exercise = { studentParticipations: [{ id: 1 }] } as Exercise;
        fixture.detectChanges();

        const textEditorComponent = fixture.debugElement.query(By.directive(TextEditorComponent)).componentInstance;
        expect(textEditorComponent).not.toBeNull();
        expect(textEditorComponent.participationId()).toBe(1);
        expect(textEditorComponent.inputSubmission().text).toBe(submissionText);
    });
});
