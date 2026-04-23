import { TestBed } from '@angular/core/testing';
import { LLMSelectionModalService } from './llm-selection-popup.service';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';

describe('LLMSelectionModalService', () => {
    let service: LLMSelectionModalService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [LLMSelectionModalService],
        });
        service = TestBed.inject(LLMSelectionModalService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('openModal$', () => {
        it('should be defined', () => {
            expect(service.openModal$).toBeDefined();
        });

        it('should emit when open is called', () => {
            return new Promise<void>((resolve) => {
                service.openModal$.subscribe({
                    next: () => {
                        resolve();
                    },
                });

                // Call open() to trigger openModal$ emission, then clean up with emitChoice
                service.open();
                service.emitChoice(LLMSelectionDecision.CLOUD_AI);
            });
        });
    });

    describe('choice$', () => {
        it('should be defined', () => {
            expect(service.choice$).toBeDefined();
        });

        it('should emit choice when emitChoice is called', () => {
            return new Promise<void>((resolve) => {
                const expectedChoice = LLMSelectionDecision.CLOUD_AI;

                service.choice$.subscribe({
                    next: (choice) => {
                        expect(choice).toBe(expectedChoice);
                        resolve();
                    },
                });

                service.emitChoice(expectedChoice);
            });
        });
    });

    describe('open', () => {
        it('should return a promise', () => {
            const result = service.open();
            expect(result).toBeInstanceOf(Promise);

            // Clean up by emitting a choice
            service.emitChoice(LLMSelectionDecision.CLOUD_AI);
        });

        it('should emit to openModalSubject', () => {
            return new Promise<void>((done) => {
                service.openModal$.subscribe(() => {
                    done();
                });

                service.open();
                service.emitChoice(LLMSelectionDecision.CLOUD_AI); // Clean up
            });
        });

        it('should resolve with CLOUD_AI choice', async () => {
            const promise = service.open();
            service.emitChoice(LLMSelectionDecision.CLOUD_AI);

            const result = await promise;
            expect(result).toBe(LLMSelectionDecision.CLOUD_AI);
        });

        it('should resolve with LOCAL_AI choice', async () => {
            const promise = service.open();
            service.emitChoice(LLMSelectionDecision.LOCAL_AI);

            const result = await promise;
            expect(result).toBe(LLMSelectionDecision.LOCAL_AI);
        });

        it('should resolve with NO_AI choice', async () => {
            const promise = service.open();
            service.emitChoice(LLMSelectionDecision.NO_AI);

            const result = await promise;
            expect(result).toBe(LLMSelectionDecision.NO_AI);
        });

        it('should resolve with DISMISSED choice', async () => {
            const promise = service.open();
            service.emitChoice(LLM_MODAL_DISMISSED);

            const result = await promise;
            expect(result).toBe(LLM_MODAL_DISMISSED);
        });

        it('should handle multiple open calls sequentially', async () => {
            const promise1 = service.open();
            service.emitChoice(LLMSelectionDecision.CLOUD_AI);
            const result1 = await promise1;

            const promise2 = service.open();
            service.emitChoice(LLMSelectionDecision.LOCAL_AI);
            const result2 = await promise2;

            expect(result1).toBe(LLMSelectionDecision.CLOUD_AI);
            expect(result2).toBe(LLMSelectionDecision.LOCAL_AI);
        });
    });

    describe('emitChoice', () => {
        it('should emit CLOUD_AI choice to choice$', () => {
            return new Promise<void>((done) => {
                service.choice$.subscribe((choice) => {
                    expect(choice).toBe(LLMSelectionDecision.CLOUD_AI);
                    done();
                });

                service.emitChoice(LLMSelectionDecision.CLOUD_AI);
            });
        });

        it('should emit LOCAL_AI choice to choice$', () => {
            return new Promise<void>((done) => {
                service.choice$.subscribe((choice) => {
                    expect(choice).toBe(LLMSelectionDecision.LOCAL_AI);
                    done();
                });

                service.emitChoice(LLMSelectionDecision.LOCAL_AI);
            });
        });

        it('should emit NO_AI choice to choice$', () => {
            return new Promise<void>((done) => {
                service.choice$.subscribe((choice) => {
                    expect(choice).toBe(LLMSelectionDecision.NO_AI);
                    done();
                });

                service.emitChoice(LLMSelectionDecision.NO_AI);
            });
        });

        it('should emit DISMISSED choice to choice$', () => {
            return new Promise<void>((done) => {
                service.choice$.subscribe((choice) => {
                    expect(choice).toBe(LLM_MODAL_DISMISSED);
                    done();
                });

                service.emitChoice(LLM_MODAL_DISMISSED);
            });
        });

        it('should allow multiple subscribers to receive the same choice', () => {
            return new Promise<void>((done) => {
                let receivedCount = 0;
                const expectedChoice = LLMSelectionDecision.CLOUD_AI;

                service.choice$.subscribe((choice) => {
                    expect(choice).toBe(expectedChoice);
                    receivedCount++;
                });

                service.choice$.subscribe((choice) => {
                    expect(choice).toBe(expectedChoice);
                    receivedCount++;
                    if (receivedCount === 2) {
                        done();
                    }
                });

                service.emitChoice(expectedChoice);
            });
        });
    });

    describe('integration between open and emitChoice', () => {
        it('should complete open promise when emitChoice is called', async () => {
            const openPromise = service.open();

            setTimeout(() => {
                service.emitChoice(LLMSelectionDecision.CLOUD_AI);
            }, 10);

            const result = await openPromise;
            expect(result).toBe(LLMSelectionDecision.CLOUD_AI);
        });

        it('should work with async/await pattern', async () => {
            const openAndRespond = async () => {
                const promise = service.open();
                setTimeout(() => service.emitChoice(LLMSelectionDecision.LOCAL_AI), 10);
                return promise;
            };

            const result = await openAndRespond();
            expect(result).toBe(LLMSelectionDecision.LOCAL_AI);
        });
    });
});
