import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { of } from 'rxjs';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { GuidedTourComponent } from 'app/guided-tour/guided-tour.component';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { courseOverviewTour } from 'app/guided-tour/tours/course-overview-tour';
import { NavbarComponent } from 'app/shared/layouts/navbar/navbar.component';
import { User } from 'app/core/user/user.model';
import { CourseCardComponent } from 'app/overview/course-card.component';
import { Course } from 'app/entities/course.model';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { FooterComponent } from 'app/shared/layouts/footer/footer.component';
import { MockDirective } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';

describe('Guided tour integration', () => {
    const user = { id: 1 } as User;
    const course = { id: 1, color: ARTEMIS_DEFAULT_COLOR } as Course;
    let guidedTourComponent: GuidedTourComponent;
    let guidedTourComponentFixture: ComponentFixture<GuidedTourComponent>;
    let navBarComponent: NavbarComponent;
    let navBarComponentFixture: ComponentFixture<NavbarComponent>;
    let courseCardComponent: CourseCardComponent;
    let courseCardComponentFixture: ComponentFixture<CourseCardComponent>;
    let footerComponentFixture: ComponentFixture<FooterComponent>;
    let guidedTourService: GuidedTourService;
    let exerciseService: ExerciseService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockDirective(NgbCollapse)],
            declarations: [GuidedTourComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ArtemisTranslatePipe, useClass: ArtemisTranslatePipe },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        guidedTourComponentFixture = TestBed.createComponent(GuidedTourComponent);
        courseCardComponentFixture = TestBed.createComponent(CourseCardComponent);
        navBarComponentFixture = TestBed.createComponent(NavbarComponent);
        footerComponentFixture = TestBed.createComponent(FooterComponent);

        guidedTourComponent = guidedTourComponentFixture.componentInstance;
        navBarComponent = navBarComponentFixture.componentInstance;
        courseCardComponent = courseCardComponentFixture.componentInstance;

        guidedTourService = TestBed.inject(GuidedTourService);
        exerciseService = TestBed.inject(ExerciseService);

        jest.spyOn(navBarComponentFixture.componentInstance, 'ngOnInit').mockImplementation(() => {
            navBarComponent.currAccount = user;
        });

        jest.spyOn<any, any>(guidedTourComponent, 'subscribeToDotChanges').mockReturnValue(of());
        jest.spyOn(exerciseService, 'getNextExercisesForDays').mockReturnValue([]);
        jest.spyOn(guidedTourService, 'init').mockImplementation();
        jest.spyOn<any, any>(guidedTourService, 'updateGuidedTourSettings').mockReturnValue(of());
        jest.spyOn<any, any>(guidedTourService, 'checkTourState').mockReturnValue(true);
        jest.spyOn<any, any>(guidedTourService, 'checkSelectorValidity').mockReturnValue(true);
        jest.spyOn<any, any>(guidedTourService, 'enableTour').mockImplementation(() => {
            guidedTourService['availableTourForComponent'] = courseOverviewTour;
            guidedTourService.currentTour = courseOverviewTour;
        });
    });

    function startGuidedTour() {
        guidedTourComponentFixture.componentInstance.ngAfterViewInit();

        // Start course overview tour
        guidedTourService['enableTour'](courseOverviewTour, true);
        guidedTourService['startTour']();

        guidedTourComponentFixture.detectChanges();
        navBarComponentFixture.detectChanges();
        expect(guidedTourComponentFixture.debugElement.query(By.css('.tour-step'))).not.toBeNull();
    }

    describe('Course Overview Tour', () => {
        beforeEach(() => {
            startGuidedTour();

            courseCardComponent.course = course;
            courseCardComponent.hasGuidedTour = true;
        });

        it('should start the course overview guided tour', () => {
            window.scrollTo = () => {};

            const guidedTourSteps = guidedTourService['getFilteredTourSteps']().length;
            guidedTourComponentFixture.autoDetectChanges(true);
            courseCardComponentFixture.autoDetectChanges(true);
            navBarComponentFixture.autoDetectChanges(true);

            expect(guidedTourService.isOnFirstStep).toBeTrue();
            expect(guidedTourSteps).toBe(9);

            // Click through tour steps in NavComponent
            for (let i = 1; i < 6; i++) {
                guidedTourService.nextStep();
                expect(guidedTourService.isOnFirstStep).toBeFalse();
                guidedTourComponent.currentTourStep = guidedTourService['currentStep'];

                if (guidedTourComponent.currentTourStep.highlightSelector) {
                    const selectedElement = navBarComponentFixture.debugElement.query(By.css(guidedTourComponent.currentTourStep.highlightSelector));
                    expect(selectedElement).not.toBeNull();
                }
            }

            // Click through tour steps in CourseCardComponent
            for (let i = 6; i < 8; i++) {
                guidedTourService.nextStep();
                guidedTourComponent.currentTourStep = guidedTourService['currentStep'];

                if (guidedTourComponent.currentTourStep.highlightSelector) {
                    const selectedElement = courseCardComponentFixture.debugElement.query(By.css(guidedTourComponent.currentTourStep.highlightSelector));
                    expect(selectedElement).not.toBeNull();
                }
            }

            // Click through tour steps in FooterComponent
            for (let i = 8; i < guidedTourSteps; i++) {
                guidedTourService.nextStep();
                guidedTourComponent.currentTourStep = guidedTourService['currentStep'];

                if (guidedTourComponent.currentTourStep.highlightSelector) {
                    const selectedElement = footerComponentFixture.debugElement.query(By.css(guidedTourComponent.currentTourStep.highlightSelector));
                    expect(selectedElement).not.toBeNull();
                }
            }

            expect(guidedTourService.isOnLastStep).toBeTrue();
        });
    });
});
