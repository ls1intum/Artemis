import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { ArtemisTestModule } from '../../../test.module';
import { MockNgbModalService } from '../../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CoursePrerequisitesModalComponent } from 'app/overview/course-registration/course-registration-prerequisites-modal/course-prerequisites-modal.component';
import { CoursePrerequisitesButtonComponent } from 'app/overview/course-registration/course-prerequisites-button/course-prerequisites-button.component';

describe('CoursePrerequisitesButtonComponent', () => {
    let fixture: ComponentFixture<CoursePrerequisitesButtonComponent>;
    let component: CoursePrerequisitesButtonComponent;
    let modalService: NgbModal;

    const course1 = {
        id: 1,
        title: 'Course A',
    } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CoursePrerequisitesButtonComponent],
            providers: [{ provide: NgbModal, useClass: MockNgbModalService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CoursePrerequisitesButtonComponent);
                component = fixture.componentInstance;
                modalService = TestBed.inject(NgbModal);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should open modal with prerequisites for course', () => {
        const openModalSpy = jest.spyOn(modalService, 'open');

        component.showPrerequisites(course1.id!);

        expect(openModalSpy).toHaveBeenCalledOnce();
        expect(openModalSpy).toHaveBeenCalledWith(CoursePrerequisitesModalComponent, { size: 'xl' });
    });
});
