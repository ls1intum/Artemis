import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { ActivatedRoute } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ParticipantScoreAverageDTO, ParticipantScoreDTO, ParticipantScoresService } from 'app/shared/participant-scores/participant-scores.service';
import * as chai from 'chai';
import { HttpResponse } from '@angular/common/http';
import { ExamParticipantScoresComponent } from 'app/exam/manage/exam-participant-scores/exam-participant-scores.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradingScale } from 'app/entities/grading-scale.model';

chai.use(sinonChai);
const expect = chai.expect;

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
    avgGrade?: String;
    @Input()
    avgRatedGrade?: String;
    @Input()
    isBonus = false;
    @Output()
    reload = new EventEmitter<void>();
}

describe('ExamParticipantScores', () => {
    let fixture: ComponentFixture<ExamParticipantScoresComponent>;
    let component: ExamParticipantScoresComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ExamParticipantScoresComponent, ParticipantScoresTableContainerStubComponent, MockPipe(ArtemisTranslatePipe), MockComponent(AlertComponent)],
            providers: [
                MockProvider(GradingSystemService),
                MockProvider(ParticipantScoresService),
                MockProvider(JhiAlertService),
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

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
    });

    it('should load date when initialized', () => {
        const participantScoreService = TestBed.inject(ParticipantScoresService);
        const gradingSystemService = TestBed.inject(GradingSystemService);

        // stub find all of exam
        const participantScoreDTO = new ParticipantScoreDTO();
        participantScoreDTO.id = 1;
        participantScoreDTO.userName = 'test';
        const findAllOfExamResponse: HttpResponse<ParticipantScoreDTO[]> = new HttpResponse({
            body: [participantScoreDTO],
            status: 200,
        });
        const findAllOfExamStub = sinon.stub(participantScoreService, 'findAllOfExam').returns(of(findAllOfExamResponse));
        // stub find average of exam
        const participantScoreAverageDTO = new ParticipantScoreAverageDTO();
        participantScoreAverageDTO.userName = 'test';
        participantScoreAverageDTO.averageScore = 10;
        const findAverageOfExamPerParticipantResponse: HttpResponse<ParticipantScoreAverageDTO[]> = new HttpResponse({
            body: [participantScoreAverageDTO],
            status: 200,
        });
        const findAverageOfExamPerParticipantStub = sinon.stub(participantScoreService, 'findAverageOfExamPerParticipant').returns(of(findAverageOfExamPerParticipantResponse));
        // stub find average of exam
        const findAverageOfExamResponse: HttpResponse<number> = new HttpResponse({
            body: 99,
            status: 200,
        });
        const findAverageOfExamStub = sinon.stub(participantScoreService, 'findAverageOfExam').returns(of(findAverageOfExamResponse));

        const gradingScaleResponseForExam: HttpResponse<GradingScale> = new HttpResponse({
            body: new GradingScale(),
            status: 200,
        });
        const findGradingScaleForExamStub = sinon.stub(gradingSystemService, 'findGradingScaleForExam').returns(of(gradingScaleResponseForExam));

        fixture.detectChanges();

        expect(component.participantScores).to.deep.equal([participantScoreDTO]);
        expect(component.participantScoresAverage).to.deep.equal([participantScoreAverageDTO]);
        expect(component.avgScore).to.equal(99);
        expect(component.avgRatedScore).to.equal(99);
        expect(findAllOfExamStub).to.have.been.called;
        expect(findAverageOfExamPerParticipantStub).to.have.been.called;
        expect(findAverageOfExamStub).to.have.been.called;
        expect(findGradingScaleForExamStub).to.have.been.called;
    });
});
