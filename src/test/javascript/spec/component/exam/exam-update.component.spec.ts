import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ExamUpdateComponent } from 'app/exam/manage/exams/exam-update.component';
import { ArtemisTestModule } from '../../test.module';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Exam } from 'app/entities/exam.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';

describe('Exam Update Component', function () {
    let component: ExamUpdateComponent;
    let fixture: ComponentFixture<ExamUpdateComponent>;
    let examManagementService: ExamManagementService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ExamUpdateComponent],
            providers: [{ provide: LocalStorageService, useClass: MockSyncStorage }, { provide: SessionStorageService, useClass: MockSyncStorage }, MockProvider(TranslateService)],
        })
            .overrideTemplate(ExamUpdateComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ExamUpdateComponent);
        component = fixture.componentInstance;
        examManagementService = fixture.debugElement.injector.get(ExamManagementService);
    });

    it('should create', () => {
        expect(component).toBeDefined();
    });

    describe('save', () => {
        it('Should call update service on save for existing entity', fakeAsync(() => {
            const exam = new Exam();
            const course = new Course();
            exam.id = 1;
            course.id = 1;
            spyOn(examManagementService, 'update').and.returnValue(of(new HttpResponse({ body: exam })));
            component.exam = exam;
            component.course = course;
            component.save();
            tick();
            expect(examManagementService.update).toHaveBeenCalledWith(1, exam);
        }));

        it('Should call create service on save for new entity', fakeAsync(() => {
            const exam = new Exam();
            const course = new Course();
            course.id = 1;
            spyOn(examManagementService, 'create').and.returnValue(of(new HttpResponse({ body: exam })));
            component.exam = exam;
            component.course = course;
            component.save();
            tick();
            expect(examManagementService.create).toHaveBeenCalledWith(1, exam);
        }));
    });
});
