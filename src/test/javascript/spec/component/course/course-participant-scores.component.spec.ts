import { CourseParticipantScoresComponent } from 'app/course/course-participant-scores/course-participant-scores.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ParticipantScoreAverageDTO, ParticipantScoreDTO, ParticipantScoresService } from 'app/shared/participant-scores/participant-scores.service';
import { HttpResponse } from '@angular/common/http';
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

describe('CourseParticipantScores', () => {
    let fixture: ComponentFixture<CourseParticipantScoresComponent>;
    let component: CourseParticipantScoresComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [CourseParticipantScoresComponent, ParticipantScoresTableContainerStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(ParticipantScoresService),
                MockProvider(AlertService),
                MockProvider(GradingSystemService),
                MockProvider(CourseManagementService),
                {
                    provide: ActivatedRoute,
                    useValue: { params: of({ courseId: 1 }) },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseParticipantScoresComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should load date when initialized', () => {
        const participantScoreService = TestBed.inject(ParticipantScoresService);
        const gradingSystemService = TestBed.inject(GradingSystemService);
        const courseManagementService = TestBed.inject(CourseManagementService);

        const course = new Course();
        course.accuracyOfScores = 1;
        jest.spyOn(courseManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));

        // Spy find all of course
        const participantScoreDTO = new ParticipantScoreDTO();
        participantScoreDTO.id = 1;
        participantScoreDTO.userName = 'test';
        const findAllOfCourseResponse: HttpResponse<ParticipantScoreDTO[]> = new HttpResponse({
            body: [participantScoreDTO],
            status: 200,
        });
        const findAllOfCourseSpy = jest.spyOn(participantScoreService, 'findAllOfCourse').mockReturnValue(of(findAllOfCourseResponse));
        // Spy find average of course
        const participantScoreAverageDTO = new ParticipantScoreAverageDTO();
        participantScoreAverageDTO.name = 'test';
        participantScoreAverageDTO.averageScore = 10;
        const findAverageOfCoursePerParticipantResponse: HttpResponse<ParticipantScoreAverageDTO[]> = new HttpResponse({
            body: [participantScoreAverageDTO],
            status: 200,
        });
        const findAverageOfCoursePerParticipantSpy = jest
            .spyOn(participantScoreService, 'findAverageOfCoursePerParticipant')
            .mockReturnValue(of(findAverageOfCoursePerParticipantResponse));
        // Spy find average of course
        const findAverageOfCourseResponse: HttpResponse<number> = new HttpResponse({
            body: 99,
            status: 200,
        });
        const findAverageOfCourseSpy = jest.spyOn(participantScoreService, 'findAverageOfCourse').mockReturnValue(of(findAverageOfCourseResponse));

        const gradingScaleResponseForCourse: HttpResponse<GradingScale> = new HttpResponse({
            body: new GradingScale(),
            status: 200,
        });
        const findGradingScaleForCourseSpy = jest.spyOn(gradingSystemService, 'findGradingScaleForCourse').mockReturnValue(of(gradingScaleResponseForCourse));

        fixture.detectChanges();

        expect(component.participantScores).toEqual([participantScoreDTO]);
        expect(component.participantScoresAverage).toEqual([participantScoreAverageDTO]);
        expect(component.avgScore).toBe(99);
        expect(component.avgRatedScore).toBe(99);
        expect(findAllOfCourseSpy).toHaveBeenCalled();
        expect(findAverageOfCoursePerParticipantSpy).toHaveBeenCalled();
        expect(findAverageOfCourseSpy).toHaveBeenCalled();
        expect(findGradingScaleForCourseSpy).toHaveBeenCalled();
    });
});
