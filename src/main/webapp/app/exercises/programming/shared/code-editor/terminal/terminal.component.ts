import { Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild, ViewEncapsulation } from '@angular/core';
import { Terminal } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';
import { LspConfigModel } from 'app/exercises/programming/shared/code-editor/model/lsp-config.model';
import { Observable, Subscription, debounceTime, throttleTime } from 'rxjs';
import { CodeEditorMonacoService } from 'app/exercises/programming/shared/code-editor/service/code-editor-monaco.service';
import { Participation } from 'app/entities/participation/participation.model';
import { getRunCommand } from 'app/exercises/programming/shared/code-editor/model/run-command.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-code-editor-terminal',
    templateUrl: './terminal.component.html',
    styleUrls: ['./terminal.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class TerminalComponent implements OnInit, OnDestroy {
    @ViewChild('terminal') terminalDiv: ElementRef;

    _lspConfig: LspConfigModel;

    @Output() onIsInitialized = new EventEmitter<void>();

    @Input()
    participation: Participation;

    @Input() set lspConfig(value: LspConfigModel) {
        this._lspConfig = value;
        if (this._lspConfig) {
            this.initTerminal();
        }
    }

    get lspConfig(): LspConfigModel {
        return this._lspConfig;
    }

    terminal: Terminal;
    terminalWs: WebSocket;
    fitAddon: FitAddon;
    isStarted = false;
    isStarting: boolean;

    resizeObserver: ResizeObserver;
    runObserver: Subscription;

    // Icons
    faTimes = faTimes;

    constructor(private monacoService: CodeEditorMonacoService) {}

    ngOnInit() {
        this.fitAddon = new FitAddon();
        const rxObservable = new Observable((subscriber) => {
            this.resizeObserver = new ResizeObserver(() => {
                subscriber.next();
            });
            this.resizeObserver.observe(document.getElementById('terminal')!);
        });

        rxObservable.pipe(debounceTime(500)).subscribe(() => {
            if (this.fitAddon) {
                this.fitAddon.fit();
            }
        });

        this.runObserver = this.monacoService.runSubject
            .asObservable()
            .pipe(throttleTime(5000))
            .subscribe((args) => {
                if (!this.terminalWs || this.terminalWs?.readyState === WebSocket.CLOSED) {
                    this.isStarted = false;
                    this.startTerminal().subscribe(() => {
                        this.fitAddon.fit();
                        if (this.terminalWs.readyState === WebSocket.OPEN) {
                            this.runCode(args);
                        }
                    });
                } else {
                    this.runCode(args);
                }
            });
    }

    ngOnDestroy() {
        this.cleanupTerminal();
        this.resizeObserver.disconnect();
        this.runObserver.unsubscribe();
    }

    initTerminal(): void {
        this.terminal = new Terminal();
        this.terminal.open(document.getElementById('terminal')!);
        this.terminal.writeln('Press "Enter" to start the terminal...');

        this.onIsInitialized.emit();

        this.terminal.onKey((keyEvent) => {
            if (!this.isStarted && keyEvent.key.charCodeAt(0) === 13) {
                if (!this.isStarting) {
                    this.isStarting = true;
                    this.startTerminal().subscribe(() => {
                        this.fitAddon.fit();
                        this.isStarting = false;
                    });
                }
            }
        });

        this.terminal.onData((data) => {
            if (this.isStarted && this.terminalWs) {
                this.terminalWs.send(data);
            }
        });

        this.terminal.onResize((size) => {
            if (this.isStarted && this.terminalWs) {
                this.terminalWs.send('\x04' + JSON.stringify({ cols: size.cols, rows: size.rows }));
            }
        });
    }

    startTerminal(): Observable<void> {
        this.terminal.loadAddon(this.fitAddon);
        return new Observable((subscriber) => {
            if (this.terminalWs && this.terminalWs?.readyState !== WebSocket.CLOSED) {
                return subscriber.next();
            }
            if (this.terminalWs) {
                this.terminalWs.close();
            }
            this.monacoService.initTerminal(this.participation.id!, this.lspConfig.serverUrl).subscribe((lspConfig) => {
                this._lspConfig = lspConfig;

                this.terminal.clear();
                this.terminal.write('Loading');
                const loadingInterval = setInterval(() => this.terminal.write('.'), 500);

                this.terminalWs = this.monacoService.initTerminalWebsocketConnection(this.lspConfig);

                this.terminalWs.onopen = () => {
                    clearInterval(loadingInterval);
                    this.terminalWs.send('clear\r'); // workaround for terminal.clear()
                    this.isStarted = true;
                    subscriber.next();
                };

                this.terminalWs.onmessage = (event) => {
                    event.data.text().then((text: string) => {
                        this.terminal.write(text);
                    });
                };

                this.terminalWs.onerror = () => {
                    clearInterval(loadingInterval);
                    this.isStarted = false;
                };

                this.terminalWs.onclose = () => {
                    this.isStarted = false;
                    clearInterval(loadingInterval);
                    this.terminal.writeln('\x1b[31m Connection Closed - Use the "Run" button to restart the terminal \x1b[0m');
                };
            });
        });
    }

    /**
     * Executes commands on the terminal in order to run the current user's code on the external monaco server.
     * @param args the arguments which will be included in the command
     */
    runCode(args: string) {
        const exercise = this.participation.exercise as ProgrammingExercise;
        const command = getRunCommand(exercise.programmingLanguage!, exercise.projectType, args);

        if (command) {
            this.terminalWs.send(new Uint8Array([3])); // CTRL-C to clear previous input
            this.terminalWs.send('\rclear\r');
            this.terminalWs.send('cd /workspace\r');
            this.terminalWs.send(command + '\r');
        } else {
            console.error('No Run command found');
        }
    }

    /**
     * Kills the terminal session
     */
    killTerminal() {
        this.terminal.dispose();
        this.terminalWs.close();
        this.initTerminal();
    }

    /**
     * Closes the Websocket connection to the external terminal.
     * @private
     */
    private cleanupTerminal() {
        if (this.terminalWs && this.terminalWs.OPEN) {
            this.terminalWs.close();
        }
    }
}
