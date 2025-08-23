import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { ProgrammingExercisePlantUmlService } from './programming-exercise-plant-uml.service';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';

describe('ProgrammingExercisePlantUmlService retry (minimal)', () => {
    let service: ProgrammingExercisePlantUmlService;
    let httpTestingController: HttpTestingController;

    const themeServiceMock = { currentTheme: jest.fn(() => Theme.LIGHT) };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(withInterceptorsFromDi()),
                provideHttpClientTesting(),
                ProgrammingExercisePlantUmlService,
                { provide: ThemeService, useValue: themeServiceMock },
            ],
        });

        service = TestBed.inject(ProgrammingExercisePlantUmlService);
        httpTestingController = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpTestingController.verify());

    it('getPlantUmlSvg should retry when first request fails', async () => {
        const plantUmlSource = '@startuml\nA->B\n@enduml';
        const resultPromise = firstValueFrom(service.getPlantUmlSvg(plantUmlSource));

        let request = httpTestingController.expectOne((req) => req.method === 'GET' && req.url.includes('api/programming/plantuml/svg'));
        expect(request.request.responseType).toBe('text');
        request.flush('err', { status: 500, statusText: 'ERR' });

        request = httpTestingController.expectOne((req) => req.method === 'GET' && req.url.includes('api/programming/plantuml/svg'));
        request.flush('<svg/>');

        await expect(resultPromise).resolves.toBe('<svg/>');
    });

    it('getPlantUmlImage should retry when first request fails', async () => {
        const plantUmlSource = '@startuml\nPNG\n@enduml';
        const resultPromise = firstValueFrom(service.getPlantUmlImage(plantUmlSource));

        let request = httpTestingController.expectOne((req) => req.method === 'GET' && req.url.includes('api/programming/plantuml/png'));
        expect(request.request.responseType).toBe('arraybuffer');
        request.flush(new ArrayBuffer(0), { status: 500, statusText: 'ERR' });

        request = httpTestingController.expectOne((req) => req.method === 'GET' && req.url.includes('api/programming/plantuml/png'));
        expect(request.request.responseType).toBe('arraybuffer');
        request.flush(new Uint8Array([1, 2, 3]).buffer);

        await expect(resultPromise).resolves.toEqual(expect.any(String));
    });
});
