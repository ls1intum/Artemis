import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { Log, LoggersResponse } from 'app/admin/logs/log.model';

import { LogsComponent } from 'app/admin/logs/logs.component';
import { LogsService } from 'app/admin/logs/logs.service';
import { of } from 'rxjs';

describe('Component Tests', () => {
    describe('LogsComponent', () => {
        let comp: LogsComponent;
        let fixture: ComponentFixture<LogsComponent>;
        let service: LogsService;

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [HttpClientTestingModule],
                declarations: [LogsComponent],
                providers: [LogsService],
            }).compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(LogsComponent);
            comp = fixture.componentInstance;
            service = TestBed.inject(LogsService);
        });

        it('should set all default values correctly', () => {
            expect(comp.filter).toBe('');
            expect(comp.orderProp).toBe('name');
            expect(comp.ascending).toBeTrue();
        });

        it('should call load all on init', () => {
            // GIVEN
            const log = new Log('main', 'WARN');
            jest.spyOn(service, 'findAll').mockReturnValue(
                of({
                    loggers: {
                        main: {
                            effectiveLevel: 'WARN',
                        },
                    },
                } as unknown as LoggersResponse),
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.findAll).toHaveBeenCalledOnce();
            expect(comp.loggers?.[0]).toEqual(expect.objectContaining(log));
        });

        it('should change log level correctly', () => {
            // GIVEN
            const log = new Log('main', 'ERROR');
            jest.spyOn(service, 'changeLevel').mockReturnValue(of({}));
            jest.spyOn(service, 'findAll').mockReturnValue(
                of({
                    loggers: {
                        main: {
                            effectiveLevel: 'ERROR',
                        },
                    },
                } as unknown as LoggersResponse),
            );

            // WHEN
            comp.changeLevel('main', 'ERROR');

            // THEN
            expect(service.changeLevel).toHaveBeenCalledOnce();
            expect(service.findAll).toHaveBeenCalledOnce();
            expect(comp.loggers?.[0]).toEqual(expect.objectContaining(log));
        });

        it('should filter loggers correctly', () => {
            comp.filter = 'test';
            comp.loggers = [
                { name: 'footestbar', level: 'DEBUG' },
                { name: 'somethingelse', level: 'DEBUG' },
            ];
            comp.filterAndSort();
            expect(comp.filteredAndOrderedLoggers).toEqual([{ name: 'footestbar', level: 'DEBUG' }]);
        });

        it('should sort loggers correctly', () => {
            // Order by name
            comp.orderProp = 'name';
            comp.loggers = [
                { name: 'test2', level: 'DEBUG' },
                { name: 'test1', level: 'WARN' },
                { name: 'test3', level: 'WARN' },
                { name: 'test3', level: 'TRACE' },
            ];

            comp.filterAndSort();

            expect(comp.filteredAndOrderedLoggers).toEqual([
                { name: 'test1', level: 'WARN' },
                { name: 'test2', level: 'DEBUG' },
                { name: 'test3', level: 'WARN' },
                { name: 'test3', level: 'TRACE' },
            ]);

            // Order by level
            comp.orderProp = 'level';
            comp.loggers = [
                { name: 'test1', level: 'WARN' },
                { name: 'test2', level: 'TRACE' },
            ];

            comp.filterAndSort();

            expect(comp.filteredAndOrderedLoggers).toEqual([
                { name: 'test2', level: 'TRACE' },
                { name: 'test1', level: 'WARN' },
            ]);

            // Order by level: If equal level, should order by name
            comp.loggers = [
                { name: 'test2', level: 'WARN' },
                { name: 'test1', level: 'WARN' },
            ];

            comp.filterAndSort();

            expect(comp.filteredAndOrderedLoggers).toEqual([
                { name: 'test1', level: 'WARN' },
                { name: 'test2', level: 'WARN' },
            ]);
        });
    });
});
