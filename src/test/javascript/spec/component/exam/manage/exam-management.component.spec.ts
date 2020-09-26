import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';

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
import { ArtemisSharedModule } from 'app/shared/shared.module';

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

    const route = ({ snapshot: { paramMap: convertToParamMap({ courseId: course.id }) }, url: new Observable<UrlSegment[]>() } as any) as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule],
            declarations: [ExamManagementComponent],
            providers: [
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
            ],
        })
            .overrideTemplate(ExamManagementComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ExamManagementComponent);
        comp = fixture.componentInstance;
        service = TestBed.inject(ExamManagementService);
        courseManagementService = TestBed.inject(CourseManagementService);
    });

    it('Should call findAllExamsForCourse on init', () => {
        // GIVEN
        const responseFakeExams = { body: [exam] } as HttpResponse<Exam[]>;
        const responseFakeCourse = { body: { id: 456 } as Course } as HttpResponse<Course>;
        sinon.replace(courseManagementService, 'find', sinon.fake.returns(of(responseFakeCourse)));
        sinon.replace(service, 'findAllExamsForCourse', sinon.fake.returns(of(responseFakeExams)));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(service.findAllExamsForCourse).to.have.been.calledOnce;
        expect(comp.exams[0]).to.eq(exam);
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
    });
});
