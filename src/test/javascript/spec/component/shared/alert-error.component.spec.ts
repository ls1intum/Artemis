import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { JhiEventManager } from 'ng-jhipster';
import { TranslateModule } from '@ngx-translate/core';

import { ArtemisTestModule } from '../../test.module';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { MockAlertService } from '../../helpers/mocks/service/mock-alert.service';
import { AlertService } from 'app/core/alert/alert.service';

describe('Component Tests', () => {
    describe('Alert Error Component', () => {
        let comp: AlertErrorComponent;
        let fixture: ComponentFixture<AlertErrorComponent>;
        let eventManager: JhiEventManager;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule, TranslateModule.forRoot()],
                declarations: [AlertErrorComponent],
                providers: [
                    JhiEventManager,
                    {
                        provide: AlertService,
                        useClass: MockAlertService,
                    },
                ],
            })
                .overrideTemplate(AlertErrorComponent, '')
                .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(AlertErrorComponent);
            comp = fixture.componentInstance;
            eventManager = fixture.debugElement.injector.get(JhiEventManager);
        });

        describe('Error Handling', () => {
            it('Should display an alert on status 0', () => {
                // GIVEN
                eventManager.broadcast({ name: 'artemisApp.httpError', content: { status: 0 } });
                // THEN
                expect(comp.alerts.length).toBe(1);
                expect(comp.alerts[0].msg).toBe('error.server.not.reachable');
            });
            it('Should display an alert on status 404', () => {
                // GIVEN
                eventManager.broadcast({ name: 'artemisApp.httpError', content: { status: 404 } });
                // THEN
                expect(comp.alerts.length).toBe(1);
                expect(comp.alerts[0].msg).toBe('error.url.not.found');
            });
            it('Should display an alert on generic error', () => {
                // GIVEN
                eventManager.broadcast({ name: 'artemisApp.httpError', content: { error: { message: 'Error Message' } } });
                eventManager.broadcast({ name: 'artemisApp.httpError', content: { error: 'Second Error Message' } });
                // THEN
                expect(comp.alerts.length).toBe(2);
                expect(comp.alerts[0].msg).toBe('Error Message');
                expect(comp.alerts[1].msg).toBe('Second Error Message');
            });
            it('Should display an alert on status 400 for generic error', () => {
                // GIVEN
                const response = new HttpErrorResponse({
                    url: 'http://localhost:8080/api/foos',
                    headers: new HttpHeaders(),
                    status: 400,
                    statusText: 'Bad Request',
                    error: {
                        type: 'https://www.jhipster.tech/problem/constraint-violation',
                        title: 'Bad Request',
                        status: 400,
                        path: '/api/foos',
                        message: 'error.validation',
                    },
                });
                eventManager.broadcast({ name: 'artemisApp.httpError', content: response });
                // THEN
                expect(comp.alerts.length).toBe(1);
                expect(comp.alerts[0].msg).toBe('error.validation');
            });
            it('Should display an alert on status 400 for generic error without message', () => {
                // GIVEN
                const response = new HttpErrorResponse({
                    url: 'http://localhost:8080/api/foos',
                    headers: new HttpHeaders(),
                    status: 400,
                    error: 'Bad Request',
                });
                eventManager.broadcast({ name: 'artemisApp.httpError', content: response });
                // THEN
                expect(comp.alerts.length).toBe(1);
                expect(comp.alerts[0].msg).toBe('Bad Request');
            });
            it('Should display an alert on status 400 for invalid parameters', () => {
                // GIVEN
                const response = new HttpErrorResponse({
                    url: 'http://localhost:8080/api/foos',
                    headers: new HttpHeaders(),
                    status: 400,
                    statusText: 'Bad Request',
                    error: {
                        type: 'https://www.jhipster.tech/problem/constraint-violation',
                        title: 'Method argument not valid',
                        status: 400,
                        path: '/api/foos',
                        message: 'error.validation',
                        fieldErrors: [{ objectName: 'foo', field: 'minField', message: 'Min' }],
                    },
                });
                eventManager.broadcast({ name: 'artemisApp.httpError', content: response });
                // THEN
                expect(comp.alerts.length).toBe(1);
                expect(comp.alerts[0].msg).toBe('error.Size');
            });
            it('Should display an alert on status 400 for error headers', () => {
                // GIVEN
                const response = new HttpErrorResponse({
                    url: 'http://localhost:8080/api/foos',
                    headers: new HttpHeaders().append('app-error', 'Error Message').append('app-params', 'foo'),
                    status: 400,
                    statusText: 'Bad Request',
                    error: {
                        status: 400,
                        message: 'error.validation',
                    },
                });
                eventManager.broadcast({ name: 'artemisApp.httpError', content: response });
                // THEN
                expect(comp.alerts.length).toBe(1);
                expect(comp.alerts[0].msg).toBe('Error Message');
            });
        });
    });
});
