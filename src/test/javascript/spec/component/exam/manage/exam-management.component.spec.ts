import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import dayjs from 'dayjs';
import { ArtemisTestModule } from '../../../test.module';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, convertToParamMap, UrlSegment } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { ExamManagementComponent } from 'app/exam/manage/exam-management.component';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { SortService } from 'app/shared/service/sort.service';
import { AccountService } from 'app/core/auth/account.service';
import { ExamInformationDTO } from 'app/entities/exam-information.model';
import { EventManager } from 'app/core/util/event-manager.service';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockRouterLinkDirective } from '../../lecture-unit/lecture-unit-management.component.spec';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { DurationPipe } from 'app/shared/pipes/artemis-duration.pipe';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';

chai.use(sinonChai);
const expect = chai.expect;

describe('Exam Management Component', () => {
    const course = { id: 456 } as Course;
    const exam = new Exam();
    exam.course = course;
    exam.id = 123;

    let comp: ExamManagementComponent;
    let fixture: ComponentFixture<ExamManagementComponent>;
    let service: ExamManagementService;
    let courseManagementService: CourseManagementService;
    let sortService: SortService;
    let accountService: AccountService;
    let eventManager: EventManager;

    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) }, url: new Observable<UrlSegment[]>() } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ExamManagementComponent,
                MockDirective(HasAnyAuthorityDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockRouterLinkDirective,
                MockDirective(SortDirective),
                MockComponent(AlertComponent),
                MockPipe(DurationPipe),
                MockDirective(DeleteButtonDirective),
            ],
            providers: [
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                EventManager,
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamManagementComponent);
        comp = fixture.componentInstance;
        service = TestBed.inject(ExamManagementService);
        courseManagementService = TestBed.inject(CourseManagementService);
        sortService = TestBed.inject(SortService);
        accountService = TestBed.inject(AccountService);
        eventManager = TestBed.inject(EventManager);
    });

    afterEach(function () {
        // completely restore all fakes created through the sandbox
        sinon.restore();
    });

    it('Should call find of courseManagementService to get course on init', () => {
        // GIVEN
        const responseFakeCourse = { body: course as Course } as HttpResponse<Course>;
        sinon.replace(courseManagementService, 'find', sinon.fake.returns(of(responseFakeCourse)));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(courseManagementService.find).to.have.been.calledOnce;
        expect(comp.course).to.eq(course);
    });

    it('Should call loadAllExamsForCourse on init', () => {
        // GIVEN
        const responseFakeCourse = { body: course as Course } as HttpResponse<Course>;
        sinon.replace(courseManagementService, 'find', sinon.fake.returns(of(responseFakeCourse)));
        const responseFakeExams = { body: [exam] } as HttpResponse<Exam[]>;
        sinon.replace(service, 'findAllExamsForCourse', sinon.fake.returns(of(responseFakeExams)));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(service.findAllExamsForCourse).to.have.been.calledOnce;
        expect(comp.exams).to.deep.eq([exam]);
    });

    it('Should call getLatestIndividualDate on init', () => {
        // GIVEN
        const responseFakeCourse = { body: course as Course } as HttpResponse<Course>;
        sinon.replace(courseManagementService, 'find', sinon.fake.returns(of(responseFakeCourse)));
        const responseFakeExams = { body: [exam] } as HttpResponse<Exam[]>;
        sinon.replace(service, 'findAllExamsForCourse', sinon.fake.returns(of(responseFakeExams)));

        const examInformationDTO = new ExamInformationDTO();
        examInformationDTO.latestIndividualEndDate = dayjs();
        const responseFakeLatestIndividualEndDateOfExam = { body: examInformationDTO } as HttpResponse<ExamInformationDTO>;
        sinon.replace(service, 'getLatestIndividualEndDateOfExam', sinon.fake.returns(of(responseFakeLatestIndividualEndDateOfExam)));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(service.getLatestIndividualEndDateOfExam).to.have.been.calledOnce;
        expect(comp.exams[0].latestIndividualEndDate).to.eq(examInformationDTO.latestIndividualEndDate);
    });

    it('Should call findAllExamsForCourse on examListModification event being fired after registering for exam changes ', () => {
        // GIVEN
        comp.course = course;
        const responseFakeExams = { body: [exam] } as HttpResponse<Exam[]>;
        sinon.replace(service, 'findAllExamsForCourse', sinon.fake.returns(of(responseFakeExams)));

        // WHEN
        comp.registerChangeInExams();
        eventManager.broadcast({ name: 'examListModification', content: 'dummy' });

        // THEN
        expect(service.findAllExamsForCourse).to.have.been.calledOnce;
        expect(comp.exams).to.deep.eq([exam]);
    });

    it('Should delete an exam when delete exam is called', () => {
        // GIVEN
        comp.exams = [exam];
        comp.course = course;
        const responseFakeDelete = {} as HttpResponse<any[]>;
        const responseFakeEmptyExamArray = { body: [exam] } as HttpResponse<Exam[]>;
        sinon.replace(service, 'delete', sinon.fake.returns(of(responseFakeDelete)));
        sinon.replace(service, 'findAllExamsForCourse', sinon.fake.returns(of(responseFakeEmptyExamArray)));

        // WHEN
        comp.deleteExam(exam.id!);

        // THEN
        expect(service.delete).to.have.been.calledOnce;
        expect(comp.exams.length).to.eq(0);
    });

    it('Should return false for examHasFinished when component has no exam information ', () => {
        // GIVEN
        exam.latestIndividualEndDate = undefined;

        // WHEN
        const examHasFinished = comp.examHasFinished(exam);

        // THEN
        expect(examHasFinished).to.be.false;
    });

    it('Should return true for examHasFinished when exam is in the past ', () => {
        // GIVEN
        exam.latestIndividualEndDate = dayjs().subtract(1, 'days');

        // WHEN
        const examHasFinished = comp.examHasFinished(exam);

        // THEN
        expect(examHasFinished).to.be.true;
    });

    it('Should return false for examHasFinished when exam is in the future ', () => {
        // GIVEN
        exam.latestIndividualEndDate = dayjs().add(1, 'minute');

        // WHEN
        const examHasFinished = comp.examHasFinished(exam);

        // THEN
        expect(examHasFinished).to.be.false;
    });

    it('Should return exam.id, when item in the exam table is being tracked ', () => {
        // WHEN
        const itemId = comp.trackId(0, exam);

        // THEN
        expect(itemId).to.eq(exam.id);
    });

    it('Should call sortService when sortRows is called ', () => {
        // GIVEN
        sinon.replace(sortService, 'sortByProperty', sinon.fake.returns([]));

        // WHEN
        comp.sortRows();

        // THEN
        expect(sortService.sortByProperty).to.have.been.calledOnce;
    });
});
