import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { ExamScoreDTO, ExerciseGroup, ExerciseInfo, ExerciseResult, StudentResult } from 'app/exam/exam-scores/exam-score-dtos.model';
import { ExamScoresComponent } from 'app/exam/exam-scores/exam-scores.component';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ParticipantScoresService, ScoresDTO } from 'app/shared/participant-scores/participant-scores.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe.ts';
import { SortService } from 'app/shared/service/sort.service';
import * as chai from 'chai';
import { cloneDeep } from 'lodash';
import { JhiAlertService, JhiSortByDirective, JhiSortDirective, JhiTranslateDirective } from 'ng-jhipster';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ChartsModule } from 'ng2-charts';
import { empty, of } from 'rxjs';

chai.use(sinonChai);
const expect = chai.expect;

describe('ExamScoresComponent', () => {
    let fixture: ComponentFixture<ExamScoresComponent>;
    let comp: ExamScoresComponent;
    let examService: ExamManagementService;

    const exInfo1 = {
        exerciseId: 11,
        title: 'ex1_1',
        maxPoints: 100,
        numberOfParticipants: 1,
    } as ExerciseInfo;
    const exInfo2 = {
        exerciseId: 12,
        title: 'ex1_2',
        maxPoints: 100,
        numberOfParticipants: 1,
    } as ExerciseInfo;

    const exGroup1Id = 1;
    const exGroup1 = {
        id: exGroup1Id,
        title: 'group',
        maxPoints: 100,
        numberOfParticipants: 2,
        containedExercises: [exInfo1, exInfo2],
    } as ExerciseGroup;

    const exResult1ForGroup1 = {
        exerciseId: 11,
        title: 'exResult1_1',
        maxScore: 100,
        achievedScore: 100,
        achievedPoints: 100,
        hasNonEmptySubmission: true,
    } as ExerciseResult;

    const exResult2ForGroup1 = {
        exerciseId: 12,
        title: 'exResult1_2',
        maxScore: 100,
        achievedScore: 20,
        achievedPoints: 20,
        hasNonEmptySubmission: true,
    } as ExerciseResult;

    const exResult3ForGroup1 = {
        exerciseId: 12,
        title: 'exResult1_2',
        maxScore: 100,
        achievedScore: 50,
        achievedPoints: 50,
        hasNonEmptySubmission: true,
    } as ExerciseResult;

    const studentResult1 = {
        userId: 1,
        name: 'user1',
        login: 'user1',
        eMail: 'user1@tum.de',
        registrationNumber: '111',
        overallPointsAchieved: 100,
        overallScoreAchieved: 100,
        submitted: true,
        exerciseGroupIdToExerciseResult: { [exGroup1Id]: exResult1ForGroup1 },
    } as StudentResult;

    const studentResult2 = {
        userId: 2,
        name: 'user2',
        login: 'user2',
        eMail: 'user2@tum.de',
        registrationNumber: '222',
        overallPointsAchieved: 20,
        overallScoreAchieved: 20,
        submitted: true,
        exerciseGroupIdToExerciseResult: { [exGroup1Id]: exResult2ForGroup1 },
    } as StudentResult;

    const studentResult3 = {
        userId: 3,
        name: 'user3',
        login: 'user3',
        eMail: 'user3@tum.de',
        registrationNumber: '333',
        overallPointsAchieved: 50,
        overallScoreAchieved: 50,
        submitted: false,
        exerciseGroupIdToExerciseResult: { [exGroup1Id]: exResult3ForGroup1 },
    } as StudentResult;

    let findExamScoresSpy: sinon.SinonStub;

    const examScoreStudent1 = new ScoresDTO();
    examScoreStudent1.studentId = studentResult1.userId;
    examScoreStudent1.pointsAchieved = studentResult1.overallPointsAchieved;
    examScoreStudent1.studentLogin = studentResult1.login;
    examScoreStudent1.scoreAchieved = studentResult1.overallScoreAchieved;
    const examScoreStudent2 = new ScoresDTO();
    examScoreStudent2.studentId = studentResult2.userId;
    examScoreStudent2.pointsAchieved = studentResult2.overallPointsAchieved;
    examScoreStudent2.studentLogin = studentResult2.login;
    examScoreStudent2.scoreAchieved = studentResult2.overallScoreAchieved;
    const examScoreStudent3 = new ScoresDTO();
    examScoreStudent3.studentId = studentResult3.userId;
    examScoreStudent3.pointsAchieved = studentResult3.overallPointsAchieved;
    examScoreStudent3.studentLogin = studentResult3.login;
    examScoreStudent3.scoreAchieved = studentResult3.overallScoreAchieved;
    const examScoreDTO = {
        examId: 1,
        title: 'exam1',
        maxPoints: 100,
        averagePointsAchieved: 60,
        exerciseGroups: [exGroup1],
        studentResults: [studentResult1, studentResult2, studentResult3],
    } as ExamScoreDTO;

    global.URL.createObjectURL = jest.fn(() => 'http://some.test.com');
    global.URL.revokeObjectURL = jest.fn(() => '');

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ChartsModule],
            declarations: [
                ExamScoresComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(AlertComponent),
                MockComponent(FaIconComponent),
                MockComponent(HelpIconComponent),
                MockDirective(JhiTranslateDirective),
                MockDirective(JhiSortByDirective),
                MockDirective(JhiSortDirective),
                MockDirective(DeleteButtonDirective),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: { params: of({ courseId: 1, examId: 1 }) } },
                MockProvider(TranslateService),
                MockProvider(ExamManagementService),
                MockProvider(SortService),
                MockProvider(JhiAlertService),
                MockProvider(ParticipantScoresService),
                MockProvider(JhiLanguageHelper, { language: empty() }),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamScoresComponent);
                comp = fixture.componentInstance;
                examService = fixture.debugElement.injector.get(ExamManagementService);
                const participationScoreService = fixture.debugElement.injector.get(ParticipantScoresService);
                findExamScoresSpy = sinon
                    .stub(participationScoreService, 'findExamScores')
                    .returns(of(new HttpResponse({ body: [examScoreStudent1, examScoreStudent2, examScoreStudent3] })));
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).to.be.ok;
    });

    it('should not log error on sentry when correct participant score calculation', () => {
        spyOn(examService, 'getExamScores').and.returnValue(of(new HttpResponse({ body: examScoreDTO })));
        fixture.detectChanges();
        const errorSpy = sinon.spy(comp, 'logErrorOnSentry');
        fixture.detectChanges();
        expect(errorSpy).to.not.have.been.called;
    });

    it('should log error on sentry when missing participant score calculation', () => {
        spyOn(examService, 'getExamScores').and.returnValue(of(new HttpResponse({ body: examScoreDTO })));
        findExamScoresSpy.returns(of(new HttpResponse({ body: [] })));
        const errorSpy = sinon.spy(comp, 'logErrorOnSentry');
        fixture.detectChanges();
        expect(errorSpy).to.have.been.calledThrice;
    });
    it('should log error on sentry when wrong points calculation', () => {
        spyOn(examService, 'getExamScores').and.returnValue(of(new HttpResponse({ body: examScoreDTO })));
        const cs1 = cloneDeep(examScoreStudent1);
        cs1.pointsAchieved = 99;
        const cs2 = cloneDeep(examScoreStudent2);
        cs2.pointsAchieved = 99;
        const cs3 = cloneDeep(examScoreStudent3);
        cs3.pointsAchieved = 99;
        findExamScoresSpy.returns(of(new HttpResponse({ body: [cs1, cs2, cs3] })));
        const errorSpy = sinon.spy(comp, 'logErrorOnSentry');
        fixture.detectChanges();
        expect(errorSpy).to.have.been.calledThrice;
    });

    it('should log error on sentry when wrong score calculation', () => {
        spyOn(examService, 'getExamScores').and.returnValue(of(new HttpResponse({ body: examScoreDTO })));
        const cs1 = cloneDeep(examScoreStudent1);
        cs1.scoreAchieved = 99;
        const cs2 = cloneDeep(examScoreStudent2);
        cs2.scoreAchieved = 99;
        const cs3 = cloneDeep(examScoreStudent3);
        cs3.scoreAchieved = 99;
        findExamScoresSpy.returns(of(new HttpResponse({ body: [cs1, cs2, cs3] })));
        const errorSpy = sinon.spy(comp, 'logErrorOnSentry');
        fixture.detectChanges();
        expect(errorSpy).to.have.been.calledThrice;
    });

    it('should make duplicated titles unique', () => {
        spyOn(examService, 'getExamScores').and.returnValue(of(new HttpResponse({ body: examScoreDTO })));

        const newExerciseGroup = new ExerciseGroup();
        newExerciseGroup.title = 'group';
        newExerciseGroup.id = 2;
        examScoreDTO.exerciseGroups.push(newExerciseGroup);
        fixture.detectChanges();

        expect(examScoreDTO.exerciseGroups[0].title).to.equal(`group (id=1)`);
        expect(examScoreDTO.exerciseGroups[1].title).to.equal(`group (id=2)`);

        // reset state
        examScoreDTO.exerciseGroups.pop();
        examScoreDTO.exerciseGroups[0].title = 'group';
    });

    it('histogram should have correct entries', () => {
        spyOn(examService, 'getExamScores').and.returnValue(of(new HttpResponse({ body: examScoreDTO })));
        fixture.detectChanges();

        expectCorrectExamScoreDto(comp, examScoreDTO);

        const noOfSubmittedExercises = examScoreDTO.studentResults.length;

        // check histogram
        expectCorrectHistogram(comp, examScoreDTO);
        // expect three distinct scores
        expect(comp.histogramData.filter((hd) => hd === 1).length).to.equal(3);
        expect(comp.noOfExamsFiltered).to.equal(noOfSubmittedExercises);

        // expect correct calculated exercise group statistics
        const groupResult1 = comp.aggregatedExerciseGroupResults.find((groupRes) => groupRes.exerciseGroupId === exGroup1.id);
        expect(groupResult1).to.be.not.undefined;

        const totalPoints = exResult1ForGroup1.achievedPoints! + exResult2ForGroup1.achievedPoints! + exResult3ForGroup1.achievedPoints!;
        expect(groupResult1!.totalPoints).to.equal(totalPoints);
        const averagePoints = totalPoints / noOfSubmittedExercises;
        expect(groupResult1!.averagePoints).to.equal(averagePoints);
        expect(groupResult1!.averagePercentage).to.equal((averagePoints / groupResult1!.maxPoints) * 100);

        // expect correct average points for exercises
        expect(groupResult1!.exerciseResults.length).to.equal(2);
        groupResult1!.exerciseResults.forEach((exResult) => {
            let averageExPoints = 0;
            let exInfo;
            if (exResult.exerciseId === 11) {
                // result for ex 1_1
                averageExPoints = exResult1ForGroup1.achievedPoints!;
                expect(exResult.averagePoints).to.equal(averageExPoints);
                exInfo = exGroup1.containedExercises.find((ex) => ex.exerciseId === 11)!;
                expect(exResult.averagePercentage).to.equal((averageExPoints / exInfo.maxPoints) * 100);
            } else if (exResult.exerciseId === 12) {
                // result for ex 1_2
                averageExPoints = (exResult2ForGroup1.achievedPoints! + exResult3ForGroup1.achievedPoints!) / 2;
                expect(exResult.averagePoints).to.equal(averageExPoints);
                exInfo = exGroup1.containedExercises.find((ex) => ex.exerciseId === 12)!;
                expect(exResult.averagePercentage).to.equal((averageExPoints / exInfo.maxPoints) * 100);
            }
        });
    });

    it('histogram should skip not submitted exams', () => {
        spyOn(examService, 'getExamScores').and.returnValue(of(new HttpResponse({ body: examScoreDTO })));
        fixture.detectChanges();
        comp.toggleFilterForSubmittedExam();

        expectCorrectExamScoreDto(comp, examScoreDTO);

        // it should skip the not submitted one
        const noOfSubmittedExercises = examScoreDTO.studentResults.length - 1;
        // check histogram
        expectCorrectHistogram(comp, examScoreDTO);
        // expect two distinct scores
        expect(comp.histogramData.filter((hd) => hd === 1).length).to.equal(noOfSubmittedExercises);
        expect(comp.noOfExamsFiltered).to.equal(noOfSubmittedExercises);

        // expect correct calculated exercise group statistics
        const groupResult1 = comp.aggregatedExerciseGroupResults.find((groupRes) => groupRes.exerciseGroupId === exGroup1.id);
        expect(groupResult1).to.be.not.undefined;

        const totalPoints = exResult1ForGroup1.achievedPoints! + exResult2ForGroup1.achievedPoints!;
        expect(groupResult1!.totalPoints).to.equal(totalPoints);
        const averagePoints = totalPoints / noOfSubmittedExercises;
        expect(groupResult1!.averagePoints).to.equal(averagePoints);
        expect(groupResult1!.averagePercentage).to.equal((averagePoints / groupResult1!.maxPoints) * 100);

        // expect correct average points for exercises
        expect(groupResult1!.exerciseResults.length).to.equal(2);
        groupResult1!.exerciseResults.forEach((exResult) => {
            let averageExPoints = 0;
            let exInfo;
            if (exResult.exerciseId === 11) {
                // result for ex 1_1
                averageExPoints = exResult1ForGroup1.achievedPoints!;
                exInfo = exGroup1.containedExercises.find((ex) => ex.exerciseId === 11)!;
                expect(exResult.averagePoints).to.equal(averageExPoints);
                expect(exResult.averagePercentage).to.equal((averageExPoints / exInfo.maxPoints) * 100);
            } else if (exResult.exerciseId === 12) {
                // result for ex 1_2
                averageExPoints = exResult2ForGroup1.achievedPoints!;
                exInfo = exGroup1.containedExercises.find((ex) => ex.exerciseId === 12)!;
                expect(exResult.averagePoints).to.equal(averageExPoints);
                expect(exResult.averagePercentage).to.equal((averageExPoints / exInfo.maxPoints) * 100);
            }
        });
    });

    it('should generate csv correctly', () => {
        const noOfSubmittedExercises = examScoreDTO.studentResults.length;
        spyOn(examService, 'getExamScores').and.returnValue(of(new HttpResponse({ body: examScoreDTO })));
        fixture.detectChanges();

        const exportAsCsvStub = sinon.stub(comp, 'exportAsCsv');
        // create csv
        comp.exportToCsv();
        const generatedRows = exportAsCsvStub.getCall(0).args[0];
        expect(generatedRows.length).to.equal(noOfSubmittedExercises);
        const user1Row = generatedRows[0];
        validateUserRow(
            user1Row,
            studentResult1.name,
            studentResult1.login,
            studentResult1.eMail,
            studentResult1.registrationNumber,
            'exResult1_1',
            '100',
            '100',
            '100',
            '100',
            studentResult1.submitted ? 'yes' : 'no',
        );
        const user2Row = generatedRows[1];
        validateUserRow(
            user2Row,
            studentResult2.name,
            studentResult2.login,
            studentResult2.eMail,
            studentResult2.registrationNumber,
            'exResult1_2',
            '20',
            '20',
            '20',
            '20',
            studentResult2.submitted ? 'yes' : 'no',
        );
        const user3Row = generatedRows[2];
        validateUserRow(
            user3Row,
            studentResult3.name,
            studentResult3.login,
            studentResult3.eMail,
            studentResult3.registrationNumber,
            'exResult1_2',
            '50',
            '50',
            '50',
            '50',
            studentResult3.submitted ? 'yes' : 'no',
        );
    });
});

function expectCorrectExamScoreDto(comp: ExamScoresComponent, examScoreDTO: ExamScoreDTO) {
    expect(comp.examScoreDTO).to.equal(examScoreDTO);
    expect(comp.studentResults).to.equal(examScoreDTO.studentResults);
    expect(comp.exerciseGroups).to.equal(examScoreDTO.exerciseGroups);
}

function expectCorrectHistogram(comp: ExamScoresComponent, examScoreDTO: ExamScoreDTO) {
    examScoreDTO.studentResults.forEach((studentResult) => {
        let histogramIndex = Math.floor(studentResult.overallScoreAchieved! / comp.binWidth);
        if (histogramIndex >= comp.histogramData.length) {
            histogramIndex = comp.histogramData.length - 1;
        }
        // expect one exercise with 20% and one with 100%
        if (studentResult.submitted || !comp.filterForSubmittedExams) {
            expect(comp.histogramData[histogramIndex]).to.equal(1);
        } else {
            // the not submitted exercise counts not towards histogram
            expect(comp.histogramData[histogramIndex]).to.equal(0);
        }
    });
}

function validateUserRow(
    userRow: any,
    expectedName: string,
    expectedUsername: string,
    expectedEmail: string,
    expectedRegistrationNumber: string,
    expectedExerciseTitle: string,
    expectedAchievedPoints: string,
    expectedAchievedScore: string,
    expectedOverAllPoints: string,
    expectedOverAllScore: string,
    expectedSubmitted: string,
) {
    expect(userRow.name).to.equal(expectedName);
    expect(userRow.login).to.equal(expectedUsername);
    expect(userRow.eMail).to.equal(expectedEmail);
    expect(userRow.registrationNumber).to.equal(expectedRegistrationNumber);
    expect(userRow['group Assigned Exercise']).to.equal(expectedExerciseTitle);
    expect(userRow['group Achieved Points']).to.equal(expectedAchievedPoints);
    expect(userRow['group Achieved Score (%)']).to.equal(expectedAchievedScore);
    expect(userRow.overAllPoints).to.equal(expectedOverAllPoints);
    expect(userRow.overAllScore).to.equal(expectedOverAllScore);
    expect(userRow.submitted).to.equal(expectedSubmitted);
}
