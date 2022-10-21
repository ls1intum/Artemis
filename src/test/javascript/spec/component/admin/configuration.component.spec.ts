import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ConfigurationComponent } from 'app/admin/configuration/configuration.component';
import { Bean, PropertySource } from 'app/admin/configuration/configuration.model';
import { ConfigurationService } from 'app/admin/configuration/configuration.service';
import { of } from 'rxjs';

describe('Component Tests', () => {
    describe('ConfigurationComponent', () => {
        let comp: ConfigurationComponent;
        let fixture: ComponentFixture<ConfigurationComponent>;
        let service: ConfigurationService;

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [HttpClientTestingModule],
                declarations: [ConfigurationComponent],
                providers: [ConfigurationService],
            })
                .overrideTemplate(ConfigurationComponent, '')
                .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ConfigurationComponent);
            comp = fixture.componentInstance;
            service = TestBed.inject(ConfigurationService);
        });

        describe('onInit', () => {
            it('should call load all on init', () => {
                // GIVEN
                const beans: Bean[] = [
                    {
                        prefix: 'jhipster',
                        properties: {
                            clientApp: {
                                name: 'jhipsterApp',
                            },
                        },
                    },
                ];
                const propertySources: PropertySource[] = [
                    {
                        name: 'server.ports',
                        properties: {
                            'local.server.port': {
                                value: '8080',
                            },
                        },
                    },
                ];
                jest.spyOn(service, 'getBeans').mockReturnValue(of(beans));
                jest.spyOn(service, 'getPropertySources').mockReturnValue(of(propertySources));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.getBeans).toHaveBeenCalledOnce();
                expect(service.getPropertySources).toHaveBeenCalledOnce();
                expect(comp.allBeans).toEqual(beans);
                expect(comp.beans).toEqual(beans);
                expect(comp.propertySources).toEqual(propertySources);
            });
        });
    });
});
