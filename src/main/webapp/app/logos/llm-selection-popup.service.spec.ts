import { TestBed } from '@angular/core/testing';
import { LLMSelectionModalService } from './llm-selection-popup.service';
import { LLMSelectionChoice } from './llm-selection-popup.component';

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
                service.emitChoice('cloud');
            });
        });
    });

    describe('choice$', () => {
        it('should be defined', () => {
            expect(service.choice$).toBeDefined();
        });

        it('should emit choice when emitChoice is called', () => {
            return new Promise<void>((resolve) => {
                const expectedChoice: LLMSelectionChoice = 'cloud';

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
            service.emitChoice('cloud');
        });

        it('should emit to openModalSubject', () => {
            return new Promise<void>((done) => {
                service.openModal$.subscribe(() => {
                    done();
                });

                service.open();
                service.emitChoice('cloud'); // Clean up
            });
        });

        it('should resolve with cloud choice', async () => {
            const promise = service.open();
            service.emitChoice('cloud');

            const result = await promise;
            expect(result).toBe('cloud');
        });

        it('should resolve with local choice', async () => {
            const promise = service.open();
            service.emitChoice('local');

            const result = await promise;
            expect(result).toBe('local');
        });

        it('should resolve with no_ai choice', async () => {
            const promise = service.open();
            service.emitChoice('no_ai');

            const result = await promise;
            expect(result).toBe('no_ai');
        });

        it('should resolve with none choice', async () => {
            const promise = service.open();
            service.emitChoice('none');

            const result = await promise;
            expect(result).toBe('none');
        });

        it('should handle multiple open calls sequentially', async () => {
            const promise1 = service.open();
            service.emitChoice('cloud');
            const result1 = await promise1;

            const promise2 = service.open();
            service.emitChoice('local');
            const result2 = await promise2;

            expect(result1).toBe('cloud');
            expect(result2).toBe('local');
        });
    });

    describe('emitChoice', () => {
        it('should emit cloud choice to choice$', () => {
            return new Promise<void>((done) => {
                service.choice$.subscribe((choice) => {
                    expect(choice).toBe('cloud');
                    done();
                });

                service.emitChoice('cloud');
            });
        });

        it('should emit local choice to choice$', () => {
            return new Promise<void>((done) => {
                service.choice$.subscribe((choice) => {
                    expect(choice).toBe('local');
                    done();
                });

                service.emitChoice('local');
            });
        });

        it('should emit no_ai choice to choice$', () => {
            return new Promise<void>((done) => {
                service.choice$.subscribe((choice) => {
                    expect(choice).toBe('no_ai');
                    done();
                });

                service.emitChoice('no_ai');
            });
        });

        it('should emit none choice to choice$', () => {
            return new Promise<void>((done) => {
                service.choice$.subscribe((choice) => {
                    expect(choice).toBe('none');
                    done();
                });

                service.emitChoice('none');
            });
        });

        it('should allow multiple subscribers to receive the same choice', () => {
            return new Promise<void>((done) => {
                let receivedCount = 0;
                const expectedChoice: LLMSelectionChoice = 'cloud';

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
                service.emitChoice('cloud');
            }, 10);

            const result = await openPromise;
            expect(result).toBe('cloud');
        });

        it('should work with async/await pattern', async () => {
            const openAndRespond = async () => {
                const promise = service.open();
                setTimeout(() => service.emitChoice('local'), 10);
                return promise;
            };

            const result = await openAndRespond();
            expect(result).toBe('local');
        });
    });
});
