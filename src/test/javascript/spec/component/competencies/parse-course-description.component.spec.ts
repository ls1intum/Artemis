import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { CompetencyFormControlsWithViewed, ParseCourseDescriptionComponent } from 'app/course/competencies/parse-description/parse-course-description.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { NgbTooltipMocksModule } from '../../helpers/mocks/directive/ngbTooltipMocks.module';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ActivatedRoute, Router } from '@angular/router';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { CourseDescriptionStubComponent } from './course-description-stub.component';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { Competency, CompetencyTaxonomy } from 'app/entities/competency.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { CompetencyRecommendationDetailComponent } from 'app/course/competencies/parse-description/competency-recommendation-detail.component';

describe('ParseCourseDescriptionComponent', () => {
    let parseCourseDescriptionComponentFixture: ComponentFixture<ParseCourseDescriptionComponent>;
    let parseCourseDescriptionComponent: ParseCourseDescriptionComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ReactiveFormsModule, NgbTooltipMocksModule],
            declarations: [
                ParseCourseDescriptionComponent,
                CourseDescriptionStubComponent,
                MockComponent(CompetencyRecommendationDetailComponent),
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
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: Router, useClass: MockRouter },
                MockProvider(CompetencyService),
                MockProvider(AlertService),
                MockProvider(ArtemisTranslatePipe),
            ],
        })
            .compileComponents()
            .then(() => {
                parseCourseDescriptionComponentFixture = TestBed.createComponent(ParseCourseDescriptionComponent);
                parseCourseDescriptionComponent = parseCourseDescriptionComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        parseCourseDescriptionComponentFixture.detectChanges();
        expect(parseCourseDescriptionComponent).toBeDefined();
    });

    it('should handle description submit', () => {
        parseCourseDescriptionComponentFixture.detectChanges();
        const getCompetencyRecommendationsSpy = jest.spyOn(parseCourseDescriptionComponent, 'getCompetencyRecommendations').mockReturnValue();

        const courseDescriptionComponent: CourseDescriptionStubComponent = parseCourseDescriptionComponentFixture.debugElement.query(
            By.directive(CourseDescriptionStubComponent),
        ).componentInstance;
        courseDescriptionComponent.formSubmitted.emit('');

        expect(getCompetencyRecommendationsSpy).toHaveBeenCalledOnce();
    });

    it('should add competency recommendations', () => {
        parseCourseDescriptionComponentFixture.detectChanges();
        const courseDescription = 'Course Description';
        const response = new HttpResponse({
            body: [new Competency(), new Competency()],
            status: 200,
        });
        const competencyService = TestBed.inject(CompetencyService);
        const getSpy = jest.spyOn(competencyService, 'getCompetenciesFromCourseDescription').mockReturnValue(of(response));

        //expect no recommendations to exist at the start
        expect(parseCourseDescriptionComponentFixture.debugElement.queryAll(By.directive(CompetencyRecommendationDetailComponent))).toHaveLength(0);
        expect(parseCourseDescriptionComponent.competencies.value).toHaveLength(0);

        parseCourseDescriptionComponent.getCompetencyRecommendations(courseDescription);
        parseCourseDescriptionComponentFixture.detectChanges();

        expect(parseCourseDescriptionComponentFixture.debugElement.queryAll(By.directive(CompetencyRecommendationDetailComponent))).toHaveLength(2);
        expect(parseCourseDescriptionComponent.competencies.value).toHaveLength(2);
        expect(getSpy).toHaveBeenCalledOnce();
    });

    it('should cancel', () => {
        parseCourseDescriptionComponentFixture.detectChanges();
        const router: Router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        const cancelButton = parseCourseDescriptionComponentFixture.debugElement.nativeElement.querySelector('#cancelButton > .jhi-btn');

        cancelButton.click();

        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should deactivate correctly', () => {
        parseCourseDescriptionComponentFixture.detectChanges();

        expect(parseCourseDescriptionComponent.canDeactivate()).toBeTrue();

        parseCourseDescriptionComponent.isLoading = true;
        expect(parseCourseDescriptionComponent.canDeactivate()).toBeFalse();

        parseCourseDescriptionComponent.submitted = true;
        expect(parseCourseDescriptionComponent.canDeactivate()).toBeTrue();
    });

    it('should not submit for unviewed recommendations', () => {
        parseCourseDescriptionComponentFixture.detectChanges();
        const modalService: NgbModal = TestBed.inject(NgbModal);
        const openSpy = jest.spyOn(modalService, 'open');
        const saveSpy = jest.spyOn(parseCourseDescriptionComponent, 'save');

        //create competency recomendations that are UNVIEWED
        parseCourseDescriptionComponent.competencies.push(createCompetencyFormGroup());
        const saveButton = parseCourseDescriptionComponentFixture.debugElement.nativeElement.querySelector('#saveButton > .jhi-btn');
        saveButton.click();

        parseCourseDescriptionComponentFixture.detectChanges();

        return parseCourseDescriptionComponentFixture.whenStable().then(() => {
            expect(openSpy).toHaveBeenCalledOnce();
            expect(saveSpy).not.toHaveBeenCalled();
        });
    });

    it('should submit', () => {
        parseCourseDescriptionComponentFixture.detectChanges();
        const router: Router = TestBed.inject(Router);
        const modalService: NgbModal = TestBed.inject(NgbModal);
        const competencyService: CompetencyService = TestBed.inject(CompetencyService);

        const navigateSpy = jest.spyOn(router, 'navigate');
        const openSpy = jest.spyOn(modalService, 'open');
        const response: HttpResponse<void> = new HttpResponse({
            status: 200,
        });
        const createBulkSpy = jest.spyOn(competencyService, 'createBulk').mockReturnValue(of(response));

        //create competency recomendations that are VIEWED
        parseCourseDescriptionComponent.competencies.push(createCompetencyFormGroup('Title', 'Descripion', CompetencyTaxonomy.ANALYZE, true));
        const saveButton = parseCourseDescriptionComponentFixture.debugElement.nativeElement.querySelector('#saveButton > .jhi-btn');
        saveButton.click();

        parseCourseDescriptionComponentFixture.detectChanges();

        return parseCourseDescriptionComponentFixture.whenStable().then(() => {
            expect(openSpy).not.toHaveBeenCalled();
            expect(createBulkSpy).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledOnce();
        });
    });

    function createCompetencyFormGroup(title?: string, description?: string, taxonomy?: CompetencyTaxonomy, viewed = false): FormGroup<CompetencyFormControlsWithViewed> {
        return new FormGroup({
            competency: new FormGroup({
                title: new FormControl(title),
                description: new FormControl(description),
                taxonomy: new FormControl(taxonomy),
            }),
            viewed: new FormControl(viewed),
        });
    }
});
