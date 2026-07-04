import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisKpiCardComponent } from './iris-kpi-card.component';
import { TranslateModule } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('IrisKpiCardComponent', () => {
    setupTestBed({ zoneless: true });

    let component: IrisKpiCardComponent;
    let fixture: ComponentFixture<IrisKpiCardComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [IrisKpiCardComponent, TranslateModule.forRoot()],
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        fixture = TestBed.createComponent(IrisKpiCardComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('titleKey', 'artemisApp.irisDashboard.totalSessions');
        fixture.componentRef.setInput('helpKey', 'artemisApp.irisDashboard.help.totalSessions');
    });

    it('should create', () => {
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should render jhi-help-icon', () => {
        fixture.detectChanges();
        const helpIcon = fixture.nativeElement.querySelector('jhi-help-icon');
        expect(helpIcon).toBeTruthy();
    });
});
