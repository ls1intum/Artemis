import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { TimeZoneWarningComponent } from 'app/shared-ui/date-time-picker/time-zone-warning.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('TimeZoneWarningComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TimeZoneWarningComponent;
    let fixture: ComponentFixture<TimeZoneWarningComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TimeZoneWarningComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TimeZoneWarningComponent);
        component = fixture.componentInstance;
    });

    it('should expose the current time zone', () => {
        expect(component.currentTimeZone).toBeDefined();
    });
});
