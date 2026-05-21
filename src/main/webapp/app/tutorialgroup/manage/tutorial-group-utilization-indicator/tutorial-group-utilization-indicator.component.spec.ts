import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupUtilizationIndicatorComponent } from 'app/tutorialgroup/manage/tutorial-group-utilization-indicator/tutorial-group-utilization-indicator.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('TutorialGroupUtilizationIndicatorComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TutorialGroupUtilizationIndicatorComponent;
    let fixture: ComponentFixture<TutorialGroupUtilizationIndicatorComponent>;
    let tutorialGroup: TutorialGroup;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialGroupUtilizationIndicatorComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupUtilizationIndicatorComponent);
        component = fixture.componentInstance;
        tutorialGroup = generateExampleTutorialGroup({});
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
