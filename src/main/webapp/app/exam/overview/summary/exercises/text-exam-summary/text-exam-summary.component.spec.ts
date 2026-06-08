import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TextExamSummaryComponent } from 'app/exam/overview/summary/exercises/text-exam-summary/text-exam-summary.component';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
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
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('TextExamSummaryComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TextExamSummaryComponent>;
    let component: TextExamSummaryComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
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
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(TextExamSummaryComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(fixture.debugElement.nativeElement.querySelector('div').innerHTML).toContain('No submission');
    });

    it('should display the submission text', () => {
        const submissionText = 'A test submission text';
        fixture.componentRef.setInput('submission', { text: submissionText } as TextSubmission);
        fixture.componentRef.setInput('exercise', { studentParticipations: [{ id: 1 }] } as Exercise);
        fixture.detectChanges();

        const textEditorComponent = fixture.debugElement.query(By.directive(TextEditorComponent)).componentInstance;
        expect(textEditorComponent).not.toBeNull();
        expect(textEditorComponent.participationId()).toBe(1);
        expect(textEditorComponent.inputSubmission().text).toBe(submissionText);
    });
});
