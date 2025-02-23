import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AffectedStudentsModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/modal/feedback-affected-students-modal.component';
import { FeedbackAffectedStudentDTO, FeedbackAnalysisService, FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import '@angular/localize/init';

describe('AffectedStudentsModalComponent', () => {
    let fixture: ComponentFixture<AffectedStudentsModalComponent>;
    let component: AffectedStudentsModalComponent;
    let feedbackService: FeedbackAnalysisService;

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
                        getParticipationForFeedbackDetailText: jest.fn().mockReturnValue(participationMock),
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(AffectedStudentsModalComponent);
        component = fixture.componentInstance;
        feedbackService = TestBed.inject(FeedbackAnalysisService);

        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('exerciseId', 1);
        fixture.componentRef.setInput('feedbackDetail', feedbackDetailMock);
        fixture.detectChanges();
    });

    it('should handle error when loadAffected fails', async () => {
        jest.spyOn(feedbackService, 'getParticipationForFeedbackDetailText').mockReturnValueOnce(Promise.reject(new Error('Error loading data')));
        const alertServiceSpy = jest.spyOn(component.alertService, 'error');
        jest.spyOn(console, 'error').mockImplementation(() => {});

        // @ts-ignore
        await component.loadAffected();

        expect(component.participation()).toEqual([]);
        expect(alertServiceSpy).toHaveBeenCalled();
    });
});
