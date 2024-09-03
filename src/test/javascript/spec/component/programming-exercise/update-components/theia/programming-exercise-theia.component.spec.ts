import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { programmingExerciseCreationConfigMock } from '../programming-exercise-creation-config-mock';
import { ProgrammingExerciseTheiaComponent } from 'app/exercises/programming/manage/update/update-components/theia/programming-exercise-theia.component';
import { TheiaService } from 'app/exercises/programming/shared/service/theia.service';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';

describe('ProgrammingExerciseTheiaComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseTheiaComponent>;
    let comp: ProgrammingExerciseTheiaComponent;

    let theiaServiceMock!: { getTheiaImages: jest.Mock };

    beforeEach(() => {
        theiaServiceMock = {
            getTheiaImages: jest.fn(),
        };
        TestBed.configureTestingModule({
            imports: [ProgrammingExerciseTheiaComponent, ArtemisSharedLibsModule],
            declarations: [MockPipe(ArtemisTranslatePipe), MockPipe(RemoveKeysPipe)],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
                {
                    provide: TheiaService,
                    useValue: theiaServiceMock,
                },
            ],
            schemas: [],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseTheiaComponent);
        comp = fixture.componentInstance;
        comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
        comp.programmingExercise = new ProgrammingExercise(undefined, undefined);
        comp.programmingExercise.allowOnlineIde = true;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', fakeAsync(() => {
        fixture.detectChanges();
        tick();
        expect(comp).not.toBeNull();
    }));

    it('should have no selectedImage when no image is available', fakeAsync(() => {
        theiaServiceMock.getTheiaImages.mockReturnValue(of({}));
        fixture.detectChanges();
        comp.loadTheiaImages();
        tick();
        expect(comp.programmingExercise.buildConfig?.theiaImage).toBeUndefined();
    }));

    it('should select first image when none was selected', fakeAsync(() => {
        theiaServiceMock.getTheiaImages.mockReturnValue(
            of({
                'Java-17': 'test-url',
                'Java-Test': 'test-url-2',
            }),
        );
        fixture.detectChanges();
        comp.loadTheiaImages();
        tick();
        expect(comp.programmingExercise.buildConfig?.theiaImage).toMatch('test-url');
    }));

    it('should not overwrite selected image when others are loaded', fakeAsync(() => {
        comp.programmingExercise.buildConfig!.theiaImage = 'test-url-2';
        theiaServiceMock.getTheiaImages.mockReturnValue(
            of({
                'Java-17': 'test-url',
                'Java-Test': 'test-url-2',
            }),
        );
        fixture.detectChanges();
        comp.loadTheiaImages();
        tick();
        expect(comp.programmingExercise.buildConfig?.theiaImage).toMatch('test-url-2');
    }));
});
