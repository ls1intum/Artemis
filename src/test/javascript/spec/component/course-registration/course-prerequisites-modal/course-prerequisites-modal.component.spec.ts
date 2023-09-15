import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { of } from 'rxjs';
import { Competency } from 'app/entities/competency.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { CoursePrerequisitesModalComponent } from 'app/overview/course-registration/course-registration-prerequisites-modal/course-prerequisites-modal.component';
import { AlertService } from 'app/core/util/alert.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyCardStubComponent } from '../../competencies/competency-card-stub.component';

describe('CoursePrerequisitesModal', () => {
    let coursePrerequisitesModalComponentFixture: ComponentFixture<CoursePrerequisitesModalComponent>;
    let coursePrerequisitesModalComponent: CoursePrerequisitesModalComponent;
    let competencyService: CompetencyService;

    const activeModalStub = {
        close: () => {},
        dismiss: () => {},
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [CoursePrerequisitesModalComponent, CompetencyCardStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(AlertService),
                MockProvider(CompetencyService),
                {
                    provide: NgbActiveModal,
                    useValue: activeModalStub,
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                coursePrerequisitesModalComponentFixture = TestBed.createComponent(CoursePrerequisitesModalComponent);
                coursePrerequisitesModalComponent = coursePrerequisitesModalComponentFixture.componentInstance;
                coursePrerequisitesModalComponentFixture.componentInstance.courseId = 1;
                competencyService = TestBed.inject(CompetencyService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load prerequisites and display a card for each of them', () => {
        const prerequisitesOfCourseResponse: HttpResponse<Competency[]> = new HttpResponse({
            body: [new Competency(), new Competency()],
            status: 200,
        });

        const getAllPrerequisitesForCourseSpy = jest.spyOn(competencyService, 'getAllPrerequisitesForCourse').mockReturnValue(of(prerequisitesOfCourseResponse));

        coursePrerequisitesModalComponentFixture.detectChanges();

        const competencyCards = coursePrerequisitesModalComponentFixture.debugElement.queryAll(By.directive(CompetencyCardStubComponent));
        expect(competencyCards).toHaveLength(2);
        expect(getAllPrerequisitesForCourseSpy).toHaveBeenCalledOnce();
        expect(coursePrerequisitesModalComponent.prerequisites).toHaveLength(2);
    });

    it('should close modal when cleared', () => {
        const dismissActiveModal = jest.spyOn(activeModalStub, 'dismiss');
        coursePrerequisitesModalComponent.clear();
        expect(dismissActiveModal).toHaveBeenCalledOnce();
    });
});
