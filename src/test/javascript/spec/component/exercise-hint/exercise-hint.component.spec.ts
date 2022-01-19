import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { TextHintComponent } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.component';
import { ArtemisTestModule } from '../../test.module';
import { TextHint } from 'app/entities/hestia/text-hint-model';
import { TextHintService } from 'app/exercises/shared/exercise-hint/manage/text-hint.service';

describe('TextHint Management Component', () => {
    let comp: TextHintComponent;
    let fixture: ComponentFixture<TextHintComponent>;
    let service: TextHintService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TextHintComponent],
            providers: [],
        })
            .overrideTemplate(TextHintComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(TextHintComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(TextHintService);
    });

    it('Should call load all on init', () => {
        // GIVEN
        const headers = new HttpHeaders().append('link', 'link;link');
        const hint = new TextHint();
        hint.id = 123;

        jest.spyOn(service, 'findByExerciseId').mockReturnValue(
            of(
                new HttpResponse({
                    body: [hint],
                    headers,
                }),
            ),
        );

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(service.findByExerciseId).toHaveBeenCalled();
        expect(comp.textHints[0]).toEqual(expect.objectContaining({ id: 123 }));
    });
});
