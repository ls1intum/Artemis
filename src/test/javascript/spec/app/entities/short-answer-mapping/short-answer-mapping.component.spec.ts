/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerMappingComponent } from 'app/entities/short-answer-mapping/short-answer-mapping.component';
import { ShortAnswerMappingService } from 'app/entities/short-answer-mapping/short-answer-mapping.service';
import { ShortAnswerMapping } from 'app/entities/short-answer-mapping/short-answer-mapping.model';

describe('Component Tests', () => {
    describe('ShortAnswerMapping Management Component', () => {
        let comp: ShortAnswerMappingComponent;
        let fixture: ComponentFixture<ShortAnswerMappingComponent>;
        let service: ShortAnswerMappingService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerMappingComponent],
                providers: []
            })
                .overrideTemplate(ShortAnswerMappingComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ShortAnswerMappingComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerMappingService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new ShortAnswerMapping(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.shortAnswerMappings[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
