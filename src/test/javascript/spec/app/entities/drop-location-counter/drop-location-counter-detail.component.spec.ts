/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { DropLocationCounterDetailComponent } from 'app/entities/drop-location-counter/drop-location-counter-detail.component';
import { DropLocationCounter } from 'app/shared/model/drop-location-counter.model';

describe('Component Tests', () => {
    describe('DropLocationCounter Management Detail Component', () => {
        let comp: DropLocationCounterDetailComponent;
        let fixture: ComponentFixture<DropLocationCounterDetailComponent>;
        const route = ({ data: of({ dropLocationCounter: new DropLocationCounter(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DropLocationCounterDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(DropLocationCounterDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(DropLocationCounterDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.dropLocationCounter).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
