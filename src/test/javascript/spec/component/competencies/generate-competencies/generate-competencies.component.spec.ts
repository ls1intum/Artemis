import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../../test.module';
import { CompetencyFormControlsWithViewed, GenerateCompetenciesComponent } from 'app/course/competencies/generate-competencies/generate-competencies.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormControl, FormGroup } from '@angular/forms';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ActivatedRoute, Router } from '@angular/router';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockNgbModalService } from '../../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Subject, of } from 'rxjs';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { CourseDescriptionFormStubComponent } from './course-description-form-stub.component';
import { MockActivatedRoute } from '../../../helpers/mocks/activated-route/mock-activated-route';
import { Competency, CompetencyTaxonomy } from 'app/entities/competency.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { CompetencyRecommendationDetailComponent } from 'app/course/competencies/generate-competencies/competency-recommendation-detail.component';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisStageStateDTO } from 'app/entities/iris/iris-stage-dto.model';
import { CourseDescriptionFormComponent } from 'app/course/competencies/generate-competencies/course-description-form.component';
import { CourseCompetencyService } from 'app/course/competencies/course-competency.service';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { CourseManagementService } from 'app/course/manage/course-management.service';

describe('GenerateCompetenciesComponent', () => {
    let generateCompetenciesComponentFixture: ComponentFixture<GenerateCompetenciesComponent>;
    let generateCompetenciesComponent: GenerateCompetenciesComponent;
    let mockWebSocketSubject: Subject<any>;

    beforeEach(() => {
        mockWebSocketSubject = new Subject<any>();

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, GenerateCompetenciesComponent, ArtemisSharedCommonModule, ArtemisSharedComponentModule, ArtemisCompetenciesModule],
            declarations: [
                CourseDescriptionFormStubComponent,
                MockComponent(CompetencyRecommendationDetailComponent),
                MockComponent(DocumentationButtonComponent),
                MockComponent(CourseDescriptionFormComponent),
                ButtonComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(FeatureToggleDirective),
                MockDirective(TranslateDirective),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ courseId: 1 }),
                },
                { provide: Router, useClass: MockRouter },
                {
                    provide: JhiWebsocketService,
                    useValue: {
                        subscribe: jest.fn(),
                        receive: jest.fn(() => mockWebSocketSubject.asObservable()),
                        unsubscribe: jest.fn(),
                    },
                },
                MockProvider(CourseDescriptionFormComponent),
                MockProvider(CourseManagementService),
                MockProvider(CourseCompetencyService),
                MockProvider(CompetencyService),
                MockProvider(AlertService),
                MockProvider(ArtemisTranslatePipe),
            ],
        })
            .overrideProvider(NgbModal, { useValue: new MockNgbModalService() })
            .compileComponents()
            .then(() => {
                generateCompetenciesComponentFixture = TestBed.createComponent(GenerateCompetenciesComponent);
                generateCompetenciesComponent = generateCompetenciesComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        generateCompetenciesComponentFixture.detectChanges();
        expect(generateCompetenciesComponent).toBeDefined();
    });

    it('should handle description submit', () => {
        generateCompetenciesComponentFixture.detectChanges();
        const getCompetencyRecommendationsSpy = jest.spyOn(generateCompetenciesComponent, 'getCompetencyRecommendations').mockReturnValue();

        const courseDescriptionComponent: CourseDescriptionFormComponent = generateCompetenciesComponentFixture.debugElement.query(
            By.directive(CourseDescriptionFormComponent),
        ).componentInstance;
        courseDescriptionComponent.formSubmitted.emit('');

        expect(getCompetencyRecommendationsSpy).toHaveBeenCalledOnce();
    });

    it('should initialize the form with the course description', fakeAsync(() => {
        generateCompetenciesComponentFixture.detectChanges();
        const courseDescription = 'Course Description';

        const courseDescriptionComponent: CourseDescriptionFormComponent = generateCompetenciesComponentFixture.debugElement.query(
            By.directive(CourseDescriptionFormComponent),
        ).componentInstance;
        const setCourseDescriptionSpy = jest.spyOn(courseDescriptionComponent, 'setCourseDescription');

        // mock the course returned by CourseManagementService
        const course = { description: courseDescription };
        const courseManagementService = TestBed.inject(CourseManagementService);
        const getCourseSpy = jest.spyOn(courseManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));

        generateCompetenciesComponent.ngOnInit();
        tick();

        expect(getCourseSpy).toHaveBeenCalledOnce();
        expect(setCourseDescriptionSpy).toHaveBeenCalledWith(courseDescription);
    }));

    it('should add competency recommendations', () => {
        generateCompetenciesComponentFixture.detectChanges();
        const courseDescription = 'Course Description';
        const response = new HttpResponse({
            body: null,
            status: 200,
        });
        const courseCompetencyService = TestBed.inject(CourseCompetencyService);
        const getSpy = jest.spyOn(courseCompetencyService, 'generateCompetenciesFromCourseDescription').mockReturnValue(of(response));

        //expect no recommendations to exist at the start
        expect(generateCompetenciesComponentFixture.debugElement.queryAll(By.directive(CompetencyRecommendationDetailComponent))).toHaveLength(0);
        expect(generateCompetenciesComponent.competencies.value).toHaveLength(0);

        generateCompetenciesComponent.getCompetencyRecommendations(courseDescription);
        const websocketMessage = {
            stages: [{ state: IrisStageStateDTO.DONE }],
            result: [{ title: 'Title', description: 'Description', taxonomy: CompetencyTaxonomy.ANALYZE }],
        };
        mockWebSocketSubject.next(websocketMessage);
        generateCompetenciesComponentFixture.detectChanges();

        expect(generateCompetenciesComponentFixture.debugElement.queryAll(By.directive(CompetencyRecommendationDetailComponent))).toHaveLength(1);
        expect(generateCompetenciesComponent.competencies.value).toHaveLength(1);
        expect(getSpy).toHaveBeenCalledOnce();
    });

    it('should open modal to remove competency recommendations', () => {
        const modalService: NgbModal = TestBed.inject(NgbModal);
        const openSpy = jest.spyOn(modalService, 'open');
        generateCompetenciesComponent.competencies.push(createCompetencyFormGroup('Title', 'Description', CompetencyTaxonomy.ANALYZE, true));
        expect(openSpy).not.toHaveBeenCalled();

        generateCompetenciesComponent.onDelete(0);

        expect(openSpy).toHaveBeenCalled();
    });

    it('should cancel', () => {
        generateCompetenciesComponentFixture.detectChanges();
        const router: Router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        const cancelButton = generateCompetenciesComponentFixture.debugElement.nativeElement.querySelector('#cancelButton > .jhi-btn');

        cancelButton.click();

        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should deactivate correctly', () => {
        generateCompetenciesComponentFixture.detectChanges();

        expect(generateCompetenciesComponent.canDeactivate()).toBeTrue();

        generateCompetenciesComponent.isLoading = true;
        expect(generateCompetenciesComponent.canDeactivate()).toBeFalse();

        generateCompetenciesComponent.submitted = true;
        expect(generateCompetenciesComponent.canDeactivate()).toBeTrue();
    });

    it('should not submit for unviewed recommendations', () => {
        generateCompetenciesComponentFixture.detectChanges();
        const modalService: NgbModal = TestBed.inject(NgbModal);
        const openSpy = jest.spyOn(modalService, 'open');
        const saveSpy = jest.spyOn(generateCompetenciesComponent, 'save');

        //create competency recomendations that are UNVIEWED
        generateCompetenciesComponent.competencies.push(createCompetencyFormGroup());
        const saveButton = generateCompetenciesComponentFixture.debugElement.nativeElement.querySelector('#saveButton > .jhi-btn');
        saveButton.click();

        generateCompetenciesComponentFixture.detectChanges();

        return generateCompetenciesComponentFixture.whenStable().then(() => {
            expect(openSpy).toHaveBeenCalledOnce();
            expect(saveSpy).not.toHaveBeenCalled();
        });
    });

    it('should submit', () => {
        generateCompetenciesComponentFixture.detectChanges();
        const router: Router = TestBed.inject(Router);
        const modalService: NgbModal = TestBed.inject(NgbModal);
        const competencyService: CompetencyService = TestBed.inject(CompetencyService);

        const navigateSpy = jest.spyOn(router, 'navigate');
        const openSpy = jest.spyOn(modalService, 'open');
        const response: HttpResponse<Competency[]> = new HttpResponse({
            body: [],
            status: 200,
        });
        const createBulkSpy = jest.spyOn(competencyService, 'createBulk').mockReturnValue(of(response));

        //create competency recomendations that are VIEWED
        generateCompetenciesComponent.competencies.push(createCompetencyFormGroup('Title', 'Description', CompetencyTaxonomy.ANALYZE, true));
        const saveButton = generateCompetenciesComponentFixture.debugElement.nativeElement.querySelector('#saveButton > .jhi-btn');
        saveButton.click();

        generateCompetenciesComponentFixture.detectChanges();

        return generateCompetenciesComponentFixture.whenStable().then(() => {
            expect(openSpy).not.toHaveBeenCalled();
            expect(createBulkSpy).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledOnce();
        });
    });

    it('should display alerts after generating', () => {
        const alertService = TestBed.inject(AlertService);
        const response = new HttpResponse({
            body: null,
            status: 200,
        });
        const courseCompetencyService = TestBed.inject(CourseCompetencyService);
        const generateCompetenciesMock = jest.spyOn(courseCompetencyService, 'generateCompetenciesFromCourseDescription').mockReturnValue(of(response));

        const successMock = jest.spyOn(alertService, 'success');
        generateCompetenciesComponent.getCompetencyRecommendations('Cool course description');
        const websocketMessage = {
            stages: [{ state: IrisStageStateDTO.DONE }],
            result: [{ title: 'Title', description: 'Description', taxonomy: CompetencyTaxonomy.ANALYZE }],
        };
        mockWebSocketSubject.next(websocketMessage);
        expect(successMock).toHaveBeenCalledOnce();
        expect(generateCompetenciesMock).toHaveBeenCalledOnce();

        const warnMock = jest.spyOn(alertService, 'warning');
        generateCompetenciesComponent.getCompetencyRecommendations('Cool course description');
        const errorMessage = {
            stages: [{ state: IrisStageStateDTO.ERROR }],
            result: [{ title: 'Title', description: 'Description', taxonomy: CompetencyTaxonomy.ANALYZE }],
        };
        mockWebSocketSubject.next(errorMessage);
        expect(warnMock).toHaveBeenCalled();
    });

    it('should not deactivate when loading', () => {
        generateCompetenciesComponent.isLoading = true;
        const canDeactivate = generateCompetenciesComponent.canDeactivate();
        expect(canDeactivate).toBeFalse();
    });

    function createCompetencyFormGroup(title?: string, description?: string, taxonomy?: CompetencyTaxonomy, viewed = false): FormGroup<CompetencyFormControlsWithViewed> {
        return new FormGroup({
            competency: new FormGroup({
                title: new FormControl(title, { nonNullable: true }),
                description: new FormControl(description, { nonNullable: true }),
                taxonomy: new FormControl(taxonomy, { nonNullable: true }),
            }),
            viewed: new FormControl(viewed, { nonNullable: true }),
        });
    }
});
