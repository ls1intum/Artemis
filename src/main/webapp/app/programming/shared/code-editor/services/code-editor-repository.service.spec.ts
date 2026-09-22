import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { DomainType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { CodeEditorRepositoryService } from './code-editor-repository.service';

describe('CodeEditorRepositoryService pull routing', () => {
    it('uses POST for explicit and current domains without changing the selected repository', () => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: WebsocketService, useValue: {} }],
        });
        const service = TestBed.inject(CodeEditorRepositoryService);
        const http = TestBed.inject(HttpTestingController);
        service.setDomain([DomainType.PARTICIPATION, { id: 12 }]);
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 18;

        service.pull([DomainType.TEST_REPOSITORY, exercise]).subscribe();
        http.expectOne({ method: 'POST', url: 'api/programming/programming-exercises/18/test-repository/pull' }).flush(null);
        service.pull().subscribe();
        http.expectOne({ method: 'POST', url: 'api/programming/participations/12/repository/pull' }).flush(null);
        http.verify();
    });
});
