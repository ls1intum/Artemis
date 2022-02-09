import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { HealthComponent } from 'app/admin/health/health.component';
import { HealthService } from 'app/admin/health/health.service';
import { Health } from 'app/admin/health/health.model';
import { By } from '@angular/platform-browser';
import { HealthModalComponent } from 'app/admin/health/health-modal.component';
import { MockComponent, MockDirective } from 'ng-mocks';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('HealthComponent', () => {
    let comp: HealthComponent;
    let fixture: ComponentFixture<HealthComponent>;
    let healthService: HealthService;
    let modalService: NgbModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [HealthComponent, MockComponent(HealthModalComponent), TranslatePipeMock, MockComponent(JhiConnectionStatusComponent), MockDirective(TranslateDirective)],
            providers: [{ provide: NgbModal, useClass: MockNgbModalService }],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(HealthComponent);
        comp = fixture.componentInstance;
        healthService = TestBed.inject(HealthService);
        modalService = TestBed.inject(NgbModal);
    });

    it('should get badge class', () => {
        const upBadgeClass = comp.getBadgeClass('UP');
        const downBadgeClass = comp.getBadgeClass('DOWN');
        expect(upBadgeClass).toEqual('bg-success');
        expect(downBadgeClass).toEqual('bg-danger');
    });

    it('should call refresh on init', () => {
        // GIVEN
        const health: Health = { status: 'UP', components: { mail: { status: 'UP', details: { mailDetail: 'mail' } } } };
        jest.spyOn(healthService, 'checkHealth').mockReturnValue(of(health));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(healthService.checkHealth).toHaveBeenCalledTimes(1);
        expect(comp.health).toBe(health);
    });

    it('should handle a 503 on refreshing health data', () => {
        // GIVEN
        const health: Health = { status: 'DOWN', components: { mail: { status: 'DOWN' } } };
        jest.spyOn(healthService, 'checkHealth').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503, error: health })));

        // WHEN
        comp.refresh();

        // THEN
        expect(healthService.checkHealth).toHaveBeenCalledTimes(1);
        expect(comp.health).toBe(health);
    });

    it('should open a modal with health if eye icon is clicked', () => {
        const health: Health = { status: 'UP', components: { mail: { status: 'UP', details: { mailDetail: 'mail' } } } };
        jest.spyOn(healthService, 'checkHealth').mockReturnValue(of(health));

        const mockModalRef = { componentInstance: {} } as NgbModalRef;
        const modalServiceSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef);

        fixture.detectChanges();

        const linkToClick = fixture.debugElement.query(By.css('a.hand'));
        expect(linkToClick).not.toBe(null);

        linkToClick.nativeElement.click();
        fixture.detectChanges();

        expect(modalServiceSpy).toHaveBeenCalledTimes(1);
        expect(modalServiceSpy).toHaveBeenCalledWith(HealthModalComponent);
        expect(mockModalRef.componentInstance.health).toEqual({ key: 'mail', value: health.components.mail });
    });
});
