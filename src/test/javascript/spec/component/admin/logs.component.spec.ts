import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

import { ArtemisTestModule } from '../../test.module';
import { LogsComponent } from 'app/admin/logs/logs.component';
import { LogsService } from 'app/admin/logs/logs.service';
import { Log, Logger, LoggersResponse } from 'app/admin/logs/log.model';

describe('LogsComponent', () => {
    let comp: LogsComponent;
    let fixture: ComponentFixture<LogsComponent>;
    let service: LogsService;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [LogsComponent],
            providers: [LogsService],
        })
            .overrideTemplate(LogsComponent, '')
            .compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(LogsComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(LogsService);
    });

    describe('OnInit', () => {
        it('should set all default values correctly', () => {
            expect(comp.filter).toBe('');
            expect(comp.orderProp).toBe('name');
        });
        it('Should call load all on init', () => {
            // GIVEN
            const logger1 = { configuredLevel: 'LEVEL', effectiveLevel: 'DEBUG' } as unknown as Logger;
            const logger2 = { configuredLevel: 'WARN', effectiveLevel: 'WARN' } as unknown as Logger;

            const loggingResponse = {
                loggers: {
                    main: logger1,
                    side: logger2,
                },
            } as unknown as LoggersResponse;
            spyOn(service, 'findAll').and.returnValue(of(loggingResponse));

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.findAll).toHaveBeenCalled();
            expect(comp.loggers![0].level).toEqual(logger1.effectiveLevel);
            expect(comp.loggers![0].name).toEqual('main');
        });
    });
    describe('change log level', () => {
        it('should change log level correctly', () => {
            // GIVEN
            const logger1 = { configuredLevel: 'LEVEL', effectiveLevel: 'DEBUG' } as unknown as Logger;
            const logger2 = { configuredLevel: 'WARN', effectiveLevel: 'WARN' } as unknown as Logger;

            const loggingResponse = {
                loggers: {
                    main: logger1,
                    side: logger2,
                },
            } as unknown as LoggersResponse;
            const log = new Log('main', 'ERROR');
            spyOn(service, 'changeLevel').and.returnValue(of(new HttpResponse()));
            spyOn(service, 'findAll').and.returnValue(of(loggingResponse));

            // WHEN
            comp.changeLevel('main', 'ERROR');

            // THEN
            expect(service.changeLevel).toHaveBeenCalled();
            expect(service.findAll).toHaveBeenCalled();
            expect(comp.loggers![0].level).toEqual(logger1.effectiveLevel);
        });
    });
});
