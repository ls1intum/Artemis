import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ParticipantScoreAverageDTO, ParticipantScoreDTO, ParticipantScoresService } from 'app/shared/participant-scores/participant-scores.service';
import { HttpResponse } from '@angular/common/http';
import { ExamParticipantScoresComponent } from 'app/exam/manage/exam-participant-scores/exam-participant-scores.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradingScale } from 'app/entities/grading-scale.model';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';

@Component({ selector: 'jhi-participant-scores-tables-container', template: '<div></div>' })
class ParticipantScoresTableContainerStubComponent {
    @Input()
    isLoading: boolean;
    @Input()
    participantScores: ParticipantScoreDTO[] = [];
    @Input()
    participantScoresAverage: ParticipantScoreAverageDTO[] = [];
    @Input()
    avgScore = 0;
    @Input()
    avgRatedScore = 0;
    @Input()
    avgGrade?: string;
    @Input()
    avgRatedGrade?: string;
    @Input()
    isBonus = false;
    @Input()
    course?: Course;
    @Output()
    reload = new EventEmitter<void>();
}

describe('ExamParticipantScores', () => {
    let fixture: ComponentFixture<ExamParticipantScoresComponent>;
    let component: ExamParticipantScoresComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ExamParticipantScoresComponent, ParticipantScoresTableContainerStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(GradingSystemService),
                MockProvider(ParticipantScoresService),
                MockProvider(AlertService),
                MockProvider(CourseManagementService),
                {
                    provide: ActivatedRoute,
                    useValue: { params: of({ courseId: 1, examId: 1 }) },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamParticipantScoresComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should load date when initialized', () => {
        const participantScoreService = TestBed.inject(ParticipantScoresService);
        const gradingSystemService = TestBed.inject(GradingSystemService);
        const courseManagementService = TestBed.inject(CourseManagementService);

        const course = new Course();
        course.accuracyOfScores = 1;
        jest.spyOn(courseManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));

        // stub find all of exam
        const participantScoreDTO = new ParticipantScoreDTO();
        participantScoreDTO.id = 1;
        participantScoreDTO.userName = 'test';
        const findAllOfExamResponse: HttpResponse<ParticipantScoreDTO[]> = new HttpResponse({
            body: [participantScoreDTO],
            status: 200,
        });
        const findAllOfExamStub = jest.spyOn(participantScoreService, 'findAllOfExam').mockReturnValue(of(findAllOfExamResponse));
        // stub find average of exam
        const participantScoreAverageDTO = new ParticipantScoreAverageDTO();
        participantScoreAverageDTO.name = 'test';
        participantScoreAverageDTO.averageScore = 10;
        const findAverageOfExamPerParticipantResponse: HttpResponse<ParticipantScoreAverageDTO[]> = new HttpResponse({
            body: [participantScoreAverageDTO],
            status: 200,
        });
        const findAverageOfExamPerParticipantStub = jest
            .spyOn(participantScoreService, 'findAverageOfExamPerParticipant')
            .mockReturnValue(of(findAverageOfExamPerParticipantResponse));
        // stub find average of exam
        const findAverageOfExamResponse: HttpResponse<number> = new HttpResponse({
            body: 99,
            status: 200,
        });
        const findAverageOfExamStub = jest.spyOn(participantScoreService, 'findAverageOfExam').mockReturnValue(of(findAverageOfExamResponse));

        const gradingScaleResponseForExam: HttpResponse<GradingScale> = new HttpResponse({
            body: new GradingScale(),
            status: 200,
        });
        const findGradingScaleForExamStub = jest.spyOn(gradingSystemService, 'findGradingScaleForExam').mockReturnValue(of(gradingScaleResponseForExam));

        fixture.detectChanges();

        expect(component.participantScores).toEqual([participantScoreDTO]);
        expect(component.participantScoresAverage).toEqual([participantScoreAverageDTO]);
        expect(component.avgScore).toBe(99);
        expect(component.avgRatedScore).toBe(99);
        expect(findAllOfExamStub).toHaveBeenCalled();
        expect(findAverageOfExamPerParticipantStub).toHaveBeenCalled();
        expect(findAverageOfExamStub).toHaveBeenCalled();
        expect(findGradingScaleForExamStub).toHaveBeenCalled();
    });
});
