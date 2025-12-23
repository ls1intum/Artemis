import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { HealthComponent } from 'app/core/admin/health/health.component';
import { HealthService } from 'app/core/admin/health/health.service';
import { Health } from 'app/core/admin/health/health.model';
import { By } from '@angular/platform-browser';
import { HealthModalComponent } from 'app/core/admin/health/health-modal.component';
import { MockDirective } from 'ng-mocks';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';

describe('HealthComponent', () => {
    let comp: HealthComponent;
    let fixture: ComponentFixture<HealthComponent>;
    let healthService: HealthService;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [HealthComponent, HealthModalComponent, TranslatePipeMock, JhiConnectionStatusComponent, MockDirective(TranslateDirective)],
            providers: [{ provide: NgbModal, useClass: MockNgbModalService }, { provide: TranslateService, useClass: MockTranslateService }, provideHttpClient()],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(HealthComponent);
                comp = fixture.componentInstance;
                healthService = TestBed.inject(HealthService);
                modalService = TestBed.inject(NgbModal);
            });
    });

    it('should get badge class', () => {
        const upBadgeClass = comp.getBadgeClass('UP');
        const downBadgeClass = comp.getBadgeClass('DOWN');
        expect(upBadgeClass).toBe('bg-success');
        expect(downBadgeClass).toBe('bg-danger');
    });

    it('should call refresh on init', () => {
        // GIVEN
        const health: Health = { status: 'UP', components: { mail: { status: 'UP', details: { mailDetail: 'mail' } } } };
        jest.spyOn(healthService, 'checkHealth').mockReturnValue(of(health));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(healthService.checkHealth).toHaveBeenCalledOnce();
        expect(comp.health).toEqual(health);
    });

    it('should handle a 503 on refreshing health data', () => {
        // GIVEN
        const health: Health = { status: 'DOWN', components: { mail: { status: 'DOWN' } } };
        jest.spyOn(healthService, 'checkHealth').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503, error: health })));

        // WHEN
        comp.refresh();

        // THEN
        expect(healthService.checkHealth).toHaveBeenCalledOnce();
        expect(comp.health).toEqual(health);
    });

    it('should open a modal with health if eye icon is clicked', () => {
        const health: Health = { status: 'UP', components: { mail: { status: 'UP', details: { mailDetail: 'mail' } } } };
        jest.spyOn(healthService, 'checkHealth').mockReturnValue(of(health));

        const mockModalRef = { componentInstance: {} } as NgbModalRef;
        const modalServiceSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef);

        fixture.detectChanges();

        const linkToClick = fixture.debugElement.query(By.css('a.hand'));
        expect(linkToClick).not.toBeNull();

        linkToClick.nativeElement.click();
        fixture.changeDetectorRef.detectChanges();

        expect(modalServiceSpy).toHaveBeenCalledOnce();
        expect(modalServiceSpy).toHaveBeenCalledWith(HealthModalComponent);
        expect(mockModalRef.componentInstance.health).toEqual({ key: 'mail', value: health.components.mail });
    });
});
