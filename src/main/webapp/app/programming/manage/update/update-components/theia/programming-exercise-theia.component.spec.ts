import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { programmingExerciseCreationConfigMock } from 'test/helpers/mocks/programming-exercise-creation-config-mock';
import { ProgrammingExerciseTheiaComponent } from 'app/programming/manage/update/update-components/theia/programming-exercise-theia.component';
import { TheiaService } from 'app/programming/shared/services/theia.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ProgrammingExerciseTheiaComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ProgrammingExerciseTheiaComponent>;
    let comp: ProgrammingExerciseTheiaComponent;

    let theiaServiceMock!: { getTheiaImages: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        theiaServiceMock = {
            // Default to an empty observable so the reload effect, which runs on the first
            // change detection, has a valid observable to subscribe to.
            getTheiaImages: vi.fn().mockReturnValue(of({})),
        };
        TestBed.configureTestingModule({
            imports: [ProgrammingExerciseTheiaComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
                {
                    provide: TheiaService,
                    useValue: theiaServiceMock,
                },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });

        fixture = TestBed.createComponent(ProgrammingExerciseTheiaComponent);
        comp = fixture.componentInstance;
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.allowOnlineIde = true;
        fixture.componentRef.setInput('programmingExerciseCreationConfig', programmingExerciseCreationConfigMock);
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    it('should have no selectedImage when no image is available', () => {
        theiaServiceMock.getTheiaImages.mockReturnValue(of({}));
        fixture.detectChanges();
        comp.loadTheiaImages();
        expect(comp.programmingExercise().buildConfig?.theiaImage).toBeUndefined();
    });

    it('should select first image when none was selected', () => {
        theiaServiceMock.getTheiaImages.mockReturnValue(
            of({
                'Java-17': 'test-url',
                'Java-Test': 'test-url-2',
            }),
        );
        fixture.detectChanges();
        comp.loadTheiaImages();
        expect(comp.programmingExercise().buildConfig?.theiaImage).toMatch('test-url');
    });

    it('should not overwrite selected image when others are loaded', () => {
        comp.programmingExercise().buildConfig!.theiaImage = 'test-url-2';
        theiaServiceMock.getTheiaImages.mockReturnValue(
            of({
                'Java-17': 'test-url',
                'Java-Test': 'test-url-2',
            }),
        );
        fixture.detectChanges();
        comp.loadTheiaImages();
        expect(comp.programmingExercise().buildConfig?.theiaImage).toMatch('test-url-2');
    });
});
