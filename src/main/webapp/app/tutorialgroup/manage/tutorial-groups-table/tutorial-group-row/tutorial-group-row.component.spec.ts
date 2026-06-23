import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { RouterModule } from '@angular/router';
import { TutorialGroupRowComponent } from 'app/tutorialgroup/manage/tutorial-groups-table/tutorial-group-row/tutorial-group-row.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import dayjs from 'dayjs/esm';
import { TutorialGroupSessionStatus } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';

describe('TutorialGroupRowComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TutorialGroupRowComponent;
    let fixture: ComponentFixture<TutorialGroupRowComponent>;
    let tutorialGroup: TutorialGroup;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialGroupRowComponent, RouterModule.forRoot([])],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupRowComponent);
        component = fixture.componentInstance;
        tutorialGroup = generateExampleTutorialGroup({});
        fixture.componentRef.setInput('showIdColumn', true);
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display the scheduled location and meeting pattern', () => {
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('Example Location');
        expect(fixture.nativeElement.textContent).toContain('global.weekdays.monday, 10:00 - 11:00');
    });

    it('should display the next session location', () => {
        tutorialGroup.nextSession = {
            id: 1,
            start: dayjs('2026-06-01T08:15:00Z'),
            end: dayjs('2026-06-01T09:45:00Z'),
            status: TutorialGroupSessionStatus.ACTIVE,
            location: 'Room DTO Smoke',
        };
        fixture.componentRef.setInput('timeZone', 'Europe/Berlin');
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('Room DTO Smoke');
    });
});
