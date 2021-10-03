import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, getTestBed, TestBed, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { StatsForDashboard } from 'app/course/dashboards/stats-for-dashboard.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { CourseManagementOverviewStatisticsDto } from 'app/course/manage/overview/course-management-overview-statistics-dto.model';
import { Course, CourseGroup } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { Organization } from 'app/entities/organization.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { LectureService } from 'app/lecture/lecture.service';
import * as chai from 'chai';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { take } from 'rxjs/operators';
import * as sinon from 'sinon';
import sinonChai from 'sinon-chai';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('Course Management Service', () => {
    let injector: TestBed;
    let service: CourseManagementService;
    let accountService: AccountService;
    let exerciseService: ExerciseService;
    let lectureService: LectureService;
    let httpMock: HttpTestingController;
    let isAtLeastTutorInCourseStub: sinon.SinonStub;
    let isAtLeastInstructorInCourseStub: sinon.SinonStub;
    let convertExercisesDateFromServerStub: sinon.SinonStub;
    let convertDatesForLecturesFromServerStub: sinon.SinonStub;
    let syncGroupsStub: sinon.SinonStub;
    const resourceUrl = SERVER_API_URL + 'api/courses';
    let course: Course;
    let exercises: Exercise[];
    let returnedFromService: any;
    let participations: StudentParticipation[];
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        injector = getTestBed();
        service = injector.get(CourseManagementService);
        httpMock = injector.get(HttpTestingController);
        accountService = injector.get(AccountService);
        exerciseService = injector.get(ExerciseService);
        lectureService = injector.get(LectureService);

        isAtLeastTutorInCourseStub = sinon.stub(accountService, 'isAtLeastTutorInCourse').returns(false);
        isAtLeastInstructorInCourseStub = sinon.stub(accountService, 'isAtLeastInstructorInCourse').returns(false);
        syncGroupsStub = sinon.stub(accountService, 'syncGroups');
        convertDatesForLecturesFromServerStub = sinon.stub(lectureService, 'convertDatesForLecturesFromServer');
        course = new Course();
        course.id = 1234;
        course.title = 'testTitle';
        exercises = [new ModelingExercise(UMLDiagramType.ComponentDiagram, undefined, undefined), new ModelingExercise(UMLDiagramType.ComponentDiagram, undefined, undefined)];
        course.exercises = exercises;
        course.lectures = undefined;
        course.startDate = undefined;
        course.endDate = undefined;
        returnedFromService = { ...course } as Course;
        participations = [new StudentParticipation()];
        convertExercisesDateFromServerStub = sinon.stub(exerciseService, 'convertExercisesDateFromServer').returns(exercises);
    });

    const expectDateConversionToBeCalled = (courseForConversion: Course) => {
        expect(convertExercisesDateFromServerStub).to.have.been.calledWith(courseForConversion.exercises);
        expect(convertDatesForLecturesFromServerStub).to.have.been.calledWith(courseForConversion.lectures);
    };

    const expectAccessRightsToBeCalled = () => {
        expect(isAtLeastTutorInCourseStub).to.have.been.called;
        expect(isAtLeastInstructorInCourseStub).to.have.been.called;
    };

    const requestAndExpectDateConversion = (method: string, url: string, flushedObject: any = returnedFromService, courseToCheck: Course, checkAccessRights?: boolean) => {
        const req = httpMock.expectOne({ method, url });
        req.flush(flushedObject);
        expectDateConversionToBeCalled(courseToCheck);
        if (checkAccessRights) {
            expectAccessRightsToBeCalled();
        }
    };

    it('should create course', fakeAsync(() => {
        delete course.id;

        service
            .create({ ...course })
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal({ ...course, id: 1234 }));

        const req = httpMock.expectOne({ method: 'POST', url: resourceUrl });
        req.flush(returnedFromService);
        tick();
    }));

    it('should update course', fakeAsync(() => {
        service
            .update({ ...course })
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal(course));

        const req = httpMock.expectOne({ method: 'PUT', url: resourceUrl });
        req.flush(returnedFromService);
        tick();
    }));

    it('should find the course', fakeAsync(() => {
        service
            .find(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}`, returnedFromService, course);
        tick();
    }));

    it('should get title of the course', fakeAsync(() => {
        returnedFromService = course.title!;
        service
            .getTitle(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal(course.title));

        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/title` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should find course with exercises', fakeAsync(() => {
        service
            .findWithExercises(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/with-exercises`, returnedFromService, course);
        tick();
    }));

    it('should find course with exercises and participations', fakeAsync(() => {
        service
            .findWithExercisesAndParticipations(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/with-exercises-and-relevant-participations`, returnedFromService, course);
        tick();
    }));

    it('should find course with organizations', fakeAsync(() => {
        course.organizations = [new Organization()];
        returnedFromService = { ...course };
        service
            .findWithOrganizations(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/with-organizations`, returnedFromService, course);
        tick();
    }));

    it('should find all courses for dashboard', fakeAsync(() => {
        returnedFromService = [{ ...course }];
        service
            .findAllForDashboard()
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal([{ ...course }]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/for-dashboard`, returnedFromService, course);
        tick();
    }));

    it('should find one course for dashboard', fakeAsync(() => {
        service
            .getCourseUpdates(course.id!)
            .pipe(take(1))
            .subscribe((updatedCourse) => {
                expect(updatedCourse).to.deep.equal(course);
            });
        service
            .findOneForDashboard(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/for-dashboard`, returnedFromService, course, true);
        tick();
    }));

    it('should find participations for the course', fakeAsync(() => {
        returnedFromService = [...participations];
        service
            .findAllParticipationsWithResults(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res).to.deep.equal(participations));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/participations` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should find reults for the course', fakeAsync(() => {
        service.findAllResultsOfCourseForExerciseAndCurrentUser(course.id!).subscribe((res) => expect(res).to.deep.equal(course));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/results` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should find all courses to register', fakeAsync(() => {
        returnedFromService = [{ ...course }];
        service
            .findAllToRegister()
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal([{ ...course }]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/to-register`, returnedFromService, course);
        tick();
    }));

    it('should find course with interesting exercises', fakeAsync(() => {
        service
            .getCourseWithInterestingExercisesForTutors(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal(course));
        requestAndExpectDateConversion('GET', `${resourceUrl}/${course.id}/for-assessment-dashboard`, returnedFromService, course);
        tick();
    }));

    it('should get stats of course', fakeAsync(() => {
        const stats = new StatsForDashboard();
        returnedFromService = { ...stats };
        service
            .getStatsForTutors(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal(stats));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/stats-for-assessment-dashboard` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should register for the course', fakeAsync(() => {
        const user = new User(1, 'name');
        service
            .registerForCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal(user));
        const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/${course.id}/register` });
        req.flush(user);
        expect(syncGroupsStub).to.have.been.calledWith(user);
        tick();
    }));

    it('should get all courses', fakeAsync(() => {
        returnedFromService = [{ ...course }];
        const params = { testParam: 'testParamValue' };
        service
            .getAll(params)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal([{ ...course }]));
        requestAndExpectDateConversion('GET', `${resourceUrl}?testParam=testParamValue`, returnedFromService, course, true);
        tick();
    }));

    it('should get all courses with quiz exercises', fakeAsync(() => {
        returnedFromService = [{ ...course }];
        service
            .getAllCoursesWithQuizExercises()
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal([{ ...course }]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/courses-with-quiz`, returnedFromService, course, true);
        tick();
    }));

    it('should get all courses together with user stats', fakeAsync(() => {
        const params = { testParam: 'testParamValue' };
        returnedFromService = [{ ...course }];
        service
            .getWithUserStats(params)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal([{ ...course }]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/with-user-stats?testParam=testParamValue`, returnedFromService, course, true);
        tick();
    }));

    it('should get all courses for overview', fakeAsync(() => {
        const params = { testParam: 'testParamValue' };
        returnedFromService = [{ ...course }];
        service
            .getCourseOverview(params)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal([{ ...course }]));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/course-management-overview?testParam=testParamValue` });
        req.flush(returnedFromService);
        expectAccessRightsToBeCalled();
        tick();
    }));

    it('should delete a course ', fakeAsync(() => {
        service
            .delete(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal({}));
        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/${course.id}` });
        req.flush({});
        tick();
    }));

    it('should get all exercise details ', fakeAsync(() => {
        returnedFromService = [{ ...course }] as Course[];
        service
            .getExercisesForManagementOverview(true)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal([{ ...course }]));
        requestAndExpectDateConversion('GET', `${resourceUrl}/exercises-for-management-overview?onlyActive=true`, returnedFromService, course);
        tick();
    }));

    it('should get all stats for overview', fakeAsync(() => {
        const stats = [new CourseManagementOverviewStatisticsDto()];
        returnedFromService = [...stats];
        service
            .getStatsForManagementOverview(true)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal(stats));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/stats-for-management-overview?onlyActive=true` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should find all categories of course', fakeAsync(() => {
        const categories = ['category1', 'category2'];
        returnedFromService = [...categories];
        service
            .findAllCategoriesOfCourse(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal(categories));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/categories` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should find all users of course group', fakeAsync(() => {
        const users = [new User(1, 'user1'), new User(2, 'user2')];
        returnedFromService = [...users];
        const courseGroup = CourseGroup.STUDENTS;
        service
            .getAllUsersInCourseGroup(course.id!, courseGroup)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal(users));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/${courseGroup}` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should find all users of course group', fakeAsync(() => {
        const expectedBlob = new Blob(['abc', 'cfe']);
        service.downloadCourseArchive(course.id!).subscribe((resp) => {
            expect(resp.body).to.equal(expectedBlob);
        });
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/download-archive` });
        req.flush(expectedBlob);
        tick();
    }));

    it('should archive the course', fakeAsync(() => {
        service.archiveCourse(course.id!).subscribe((res) => expect(res.body).to.deep.equal(course));
        const req = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/${course.id}/archive` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should clean up the course', fakeAsync(() => {
        service.cleanupCourse(course.id!).subscribe((res) => expect(res.body).to.deep.equal(course));
        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/${course.id}/cleanup` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should find all locked submissions of course', fakeAsync(() => {
        const submission = new ModelingSubmission();
        const submissions = [submission];
        returnedFromService = [...submissions];
        service.findAllLockedSubmissionsOfCourse(course.id!).subscribe((res) => expect(res.body).to.deep.equal(submissions));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/lockedSubmissions` });
        req.flush(returnedFromService);
        tick();
    }));

    it('should add user to course group', fakeAsync(() => {
        const user = new User(1, 'name');
        const courseGroup = CourseGroup.STUDENTS;
        service
            .addUserToCourseGroup(course.id!, courseGroup, user.login!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal({}));
        const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/${course.id}/${courseGroup}/${user.login}` });
        req.flush({});
        tick();
    }));

    it('should remove user from course group', fakeAsync(() => {
        const user = new User(1, 'name');
        const courseGroup = CourseGroup.STUDENTS;
        service
            .removeUserFromCourseGroup(course.id!, courseGroup, user.login!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).to.deep.equal({}));
        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/${course.id}/${courseGroup}/${user.login}` });
        req.flush({});
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
        sinon.restore();
    });
});
