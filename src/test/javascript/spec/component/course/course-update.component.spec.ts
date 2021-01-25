import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { FormControl, FormGroup } from '@angular/forms';
import { ArtemisTestModule } from '../../test.module';
import { CourseUpdateComponent } from 'app/course/manage/course-update.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';

describe('Course Management Update Component', () => {
    let comp: CourseUpdateComponent;
    let fixture: ComponentFixture<CourseUpdateComponent>;
    let service: CourseManagementService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [{ provide: LocalStorageService, useClass: MockSyncStorage }, { provide: SessionStorageService, useClass: MockSyncStorage }, MockProvider(TranslateService)],
            declarations: [CourseUpdateComponent],
        })
            .overrideTemplate(CourseUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(CourseUpdateComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(CourseManagementService);
    });

    describe('save', () => {
        it('Should call update service on save for existing entity', fakeAsync(() => {
            // GIVEN
            const entity = new Course();
            entity.id = 123;
            spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.course = entity;
            comp.courseForm = new FormGroup({
                id: new FormControl(entity.id),
                onlineCourse: new FormControl(entity.onlineCourse),
                registrationEnabled: new FormControl(entity.registrationEnabled),
                presentationScore: new FormControl(entity.presentationScore),
                maxComplaints: new FormControl(entity.maxComplaints),
                maxTeamComplaints: new FormControl(entity.maxTeamComplaints),
                maxComplaintTimeDays: new FormControl(entity.maxComplaintTimeDays),
                complaintsEnabled: new FormControl(entity.complaintsEnabled),
                studentQuestionsEnabled: new FormControl(entity.studentQuestionsEnabled),
                requestMoreFeedbackEnabled: new FormControl(entity.requestMoreFeedbackEnabled),
                maxRequestMoreFeedbackTimeDays: new FormControl(entity.maxRequestMoreFeedbackTimeDays),
                isAtLeastTutor: new FormControl(entity.isAtLeastTutor),
                isAtLeastInstructor: new FormControl(entity.isAtLeastInstructor),
            });
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.update).toHaveBeenCalledWith(entity);
            expect(comp.isSaving).toEqual(false);
        }));

        it('Should call create service on save for new entity', fakeAsync(() => {
            // GIVEN
            const entity = new Course();
            spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
            comp.course = entity;
            comp.courseForm = new FormGroup({
                onlineCourse: new FormControl(entity.onlineCourse),
                registrationEnabled: new FormControl(entity.registrationEnabled),
                presentationScore: new FormControl(entity.presentationScore),
                maxComplaints: new FormControl(entity.maxComplaints),
                maxTeamComplaints: new FormControl(entity.maxTeamComplaints),
                maxComplaintTimeDays: new FormControl(entity.maxComplaintTimeDays),
                complaintsEnabled: new FormControl(entity.complaintsEnabled),
                studentQuestionsEnabled: new FormControl(entity.studentQuestionsEnabled),
                requestMoreFeedbackEnabled: new FormControl(entity.requestMoreFeedbackEnabled),
                maxRequestMoreFeedbackTimeDays: new FormControl(entity.maxRequestMoreFeedbackTimeDays),
                isAtLeastTutor: new FormControl(entity.isAtLeastTutor),
                isAtLeastInstructor: new FormControl(entity.isAtLeastInstructor),
            }); // mocking reactive form
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.create).toHaveBeenCalledWith(entity);
            expect(comp.isSaving).toEqual(false);
        }));
    });
});
