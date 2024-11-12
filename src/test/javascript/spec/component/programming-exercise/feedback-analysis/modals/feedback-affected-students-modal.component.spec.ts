import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AffectedStudentsModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/Modal/feedback-affected-students-modal.component';
import { FeedbackAffectedStudentDTO, FeedbackAnalysisService, FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import '@angular/localize/init';

describe('AffectedStudentsModalComponent', () => {
    let fixture: ComponentFixture<AffectedStudentsModalComponent>;
    let component: AffectedStudentsModalComponent;
    let feedbackService: FeedbackAnalysisService;

    const feedbackDetailMock: FeedbackDetail = {
        concatenatedFeedbackIds: [1, 2],
        count: 5,
        relativeCount: 25.0,
        detailText: 'Some feedback detail',
        testCaseName: 'testCase1',
        taskName: '1',
        errorCategory: 'StudentError',
    };

    const participationMock: FeedbackAffectedStudentDTO[] = [
        { courseId: 1, participationId: 101, firstName: 'John', lastName: 'Doe', login: 'johndoe', repositoryURI: 'repo1' },
        { courseId: 1, participationId: 102, firstName: 'Jane', lastName: 'Smith', login: 'janesmith', repositoryURI: 'repo2' },
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
                        getParticipationForFeedbackIds: jest.fn().mockReturnValue(of({ content: participationMock, totalPages: 1, totalElements: 2 })),
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(AffectedStudentsModalComponent);
        component = fixture.componentInstance;
        feedbackService = TestBed.inject(FeedbackAnalysisService);

        fixture.componentRef.setInput('exerciseId', 1);
        fixture.componentRef.setInput('feedbackDetail', feedbackDetailMock);
        fixture.detectChanges();
    });

    it('should update page and reload data when setPage is called', async () => {
        const loadAffectedSpy = jest.spyOn(component as any, 'loadAffected');

        component.setPage(2);
        expect(component.page()).toBe(2);
        expect(loadAffectedSpy).toHaveBeenCalledOnce();
    });

    it('should handle error when loadAffected fails', async () => {
        jest.spyOn(feedbackService, 'getParticipationForFeedbackIds').mockReturnValueOnce(Promise.reject(new Error('Error loading data')));
        const consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

        await component['loadAffected']();

        expect(component.participation().content).toEqual([]);
        expect(consoleErrorSpy).toHaveBeenCalled();

        consoleErrorSpy.mockRestore();
    });
});
