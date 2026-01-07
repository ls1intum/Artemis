import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { User } from 'app/core/user/user.model';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { RouterModule } from '@angular/router';
import { CourseTutorialGroupCardComponent } from 'app/tutorialgroup/overview/course-tutorial-group-card/course-tutorial-group-card.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('CourseTutorialGroupCardComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CourseTutorialGroupCardComponent;
    let fixture: ComponentFixture<CourseTutorialGroupCardComponent>;
    let exampleTutorialGroup: TutorialGroup;
    let exampleTA: User;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [RouterModule.forRoot([]), CourseTutorialGroupCardComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseTutorialGroupCardComponent);
        component = fixture.componentInstance;
        exampleTA = { id: 1, name: 'TA' } as User;
        exampleTutorialGroup = generateExampleTutorialGroup({ teachingAssistant: exampleTA });
        fixture.componentRef.setInput('tutorialGroup', exampleTutorialGroup);
        fixture.componentRef.setInput('course', { id: 1 });
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
