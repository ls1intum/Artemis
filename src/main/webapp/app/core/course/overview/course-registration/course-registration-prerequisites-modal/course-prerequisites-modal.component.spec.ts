import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';
import { CoursePrerequisitesModalComponent } from 'app/core/course/overview/course-registration/course-registration-prerequisites-modal/course-prerequisites-modal.component';
import { AlertService } from 'app/shared/service/alert.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { PrerequisiteService } from 'app/atlas/manage/services/prerequisite.service';
import { HttpResponse } from '@angular/common/http';
import { CompetencyCardComponent } from 'app/atlas/overview/competency-card/competency-card.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CoursePrerequisitesModal', () => {
    setupTestBed({ zoneless: true });

    let coursePrerequisitesModalComponentFixture: ComponentFixture<CoursePrerequisitesModalComponent>;
    let coursePrerequisitesModalComponent: CoursePrerequisitesModalComponent;
    let prerequisiteService: PrerequisiteService;

    const activeModalStub = {
        close: () => {},
        dismiss: () => {},
    };

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [CoursePrerequisitesModalComponent, MockPipe(ArtemisTranslatePipe), MockComponent(CompetencyCardComponent)],
            providers: [
                MockProvider(AlertService),
                MockProvider(PrerequisiteService),
                {
                    provide: NgbActiveModal,
                    useValue: activeModalStub,
                },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        await TestBed.compileComponents();
        coursePrerequisitesModalComponentFixture = TestBed.createComponent(CoursePrerequisitesModalComponent);
        coursePrerequisitesModalComponent = coursePrerequisitesModalComponentFixture.componentInstance;
        coursePrerequisitesModalComponentFixture.componentRef.setInput('courseId', 1);
        prerequisiteService = TestBed.inject(PrerequisiteService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load prerequisites and display a card for each of them', () => {
        const getAllPrerequisitesForCourseSpy = vi
            .spyOn(prerequisiteService, 'getAllForCourse')
            .mockReturnValue(of(new HttpResponse({ body: [{ id: 1 }, { id: 2 }], status: 200 })));

        coursePrerequisitesModalComponentFixture.detectChanges();

        const competencyCards = coursePrerequisitesModalComponentFixture.debugElement.queryAll(By.directive(CompetencyCardComponent));
        expect(competencyCards).toHaveLength(2);
        expect(getAllPrerequisitesForCourseSpy).toHaveBeenCalledOnce();
        expect(coursePrerequisitesModalComponent.prerequisites).toHaveLength(2);
    });

    it('should close modal when cleared', () => {
        const dismissActiveModal = vi.spyOn(activeModalStub, 'dismiss');
        coursePrerequisitesModalComponent.clear();
        expect(dismissActiveModal).toHaveBeenCalledOnce();
    });
});
