import { CourseParticipantScoresComponent } from 'app/course/course-participant-scores/course-participant-scores.component';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslatePipe } from '@ngx-translate/core';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { of } from 'rxjs';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { ActivatedRoute } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ParticipantScoreAverageDTO, ParticipantScoreDTO, ParticipantScoresService } from 'app/shared/participant-scores/participant-scores.service';
import * as chai from 'chai';
import { HttpResponse } from '@angular/common/http';

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
    @Output()
    reload = new EventEmitter<void>();
}

describe('CourseParticipantScores', () => {
    let fixture: ComponentFixture<CourseParticipantScoresComponent>;
    let component: CourseParticipantScoresComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [CourseParticipantScoresComponent, ParticipantScoresTableContainerStubComponent, MockPipe(TranslatePipe), MockComponent(AlertComponent)],
            providers: [
                MockProvider(ParticipantScoresService),
                MockProvider(JhiAlertService),
                MockProvider(LearningGoalService),
                {
                    provide: ActivatedRoute,
                    useValue: { params: of({ courseId: 1 }) },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseParticipantScoresComponent);
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

        // stub find all of course
        const participantScoreDTO = new ParticipantScoreDTO();
        participantScoreDTO.id = 1;
        participantScoreDTO.userName = 'test';
        const findAllOfCourseResponse: HttpResponse<ParticipantScoreDTO[]> = new HttpResponse({
            body: [participantScoreDTO],
            status: 200,
        });
        const findAllOfCourseStub = sinon.stub(participantScoreService, 'findAllOfCourse').returns(of(findAllOfCourseResponse));
        // stub find average of course
        const participantScoreAverageDTO = new ParticipantScoreAverageDTO();
        participantScoreAverageDTO.userName = 'test';
        participantScoreAverageDTO.averageScore = 10;
        const findAverageOfCoursePerParticipantResponse: HttpResponse<ParticipantScoreAverageDTO[]> = new HttpResponse({
            body: [participantScoreAverageDTO],
            status: 200,
        });
        const findAverageOfCoursePerParticipantStub = sinon
            .stub(participantScoreService, 'findAverageOfCoursePerParticipant')
            .returns(of(findAverageOfCoursePerParticipantResponse));
        // stub find average of course
        const findAverageOfCourseResponse: HttpResponse<number> = new HttpResponse({
            body: 99,
            status: 200,
        });
        const findAverageOfCourseStub = sinon.stub(participantScoreService, 'findAverageOfCourse').returns(of(findAverageOfCourseResponse));

        fixture.detectChanges();

        expect(component.participantScores).to.deep.equal([participantScoreDTO]);
        expect(component.participantScoresAverage).to.deep.equal([participantScoreAverageDTO]);
        expect(component.avgScore).to.equal(99);
        expect(component.avgRatedScore).to.equal(99);
        expect(findAllOfCourseStub).to.have.been.called;
        expect(findAverageOfCoursePerParticipantStub).to.have.been.called;
        expect(findAverageOfCourseStub).to.have.been.called;
    });
});
