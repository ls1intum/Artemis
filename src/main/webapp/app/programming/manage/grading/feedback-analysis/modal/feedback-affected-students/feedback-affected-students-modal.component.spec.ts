import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AffectedStudentsModalComponent } from 'app/programming/manage/grading/feedback-analysis/modal/feedback-affected-students/feedback-affected-students-modal.component';
import { FeedbackAffectedStudentDTO, FeedbackAnalysisService, FeedbackDetail } from 'app/programming/manage/grading/feedback-analysis/service/feedback-analysis.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import '@angular/localize/init';

/**
 * Typed view onto the protected `loadAffected` method so the spec can invoke it
 * without a blanket `(component as any)` cast.
 */
type ModalInternals = AffectedStudentsModalComponent & {
    loadAffected(): Promise<void>;
};
const internals = (c: AffectedStudentsModalComponent): ModalInternals => c as ModalInternals;

describe('AffectedStudentsModalComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<AffectedStudentsModalComponent>;
    let component: AffectedStudentsModalComponent;
    const feedbackDetailMock: FeedbackDetail = {
        feedbackIds: [1, 2, 3, 4, 5],
        count: 5,
        relativeCount: 25.0,
        detailTexts: ['Some feedback detail'],
        testCaseName: 'testCase1',
        taskName: '1',
        errorCategory: 'StudentError',
        hasLongFeedbackText: false,
    };

    const participationMock: FeedbackAffectedStudentDTO[] = [
        { participationId: 101, firstName: 'A', lastName: 'Z', login: 'AZ', repositoryURI: 'repo1' },
        { participationId: 102, firstName: 'I', lastName: 'B', login: 'IB', repositoryURI: 'repo2' },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), AffectedStudentsModalComponent],
            providers: [
                provideHttpClient(),
                NgbActiveModal,
                {
                    provide: FeedbackAnalysisService,
                    useValue: {
                        getParticipationForFeedbackDetailText: vi.fn().mockReturnValue(participationMock),
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(AffectedStudentsModalComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('exerciseId', 1);
        fixture.componentRef.setInput('feedbackDetail', feedbackDetailMock);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should handle error when loadAffected fails', async () => {
        vi.spyOn(component.feedbackService, 'getParticipationForFeedbackDetailText').mockReturnValue(Promise.reject(new Error('Error loading data')));
        const alertServiceSpy = vi.spyOn(component.alertService, 'error');
        vi.spyOn(console, 'error').mockImplementation(() => {});

        await internals(component).loadAffected();

        expect(component.participation()).toEqual([]);
        expect(alertServiceSpy).toHaveBeenCalled();
    });
});
