import { RequestMessage, WebSocketMessageWriter } from 'vscode-ws-jsonrpc';

type End = {
    line: number;
    character: number;
};

type Start = {
    line: number;
    character: number;
};

type Range = {
    start: Start;
    end: End;
};

type ContentChange = {
    range: Range;
    rangeLength: number;
    text: string;
};

type DidChangeParams = {
    textDocument: object;
    contentChanges: ContentChange[];
};

interface DidChangeRequestMessage extends RequestMessage {
    params: DidChangeParams;
}

export class CustomWebSocketMessageWriter extends WebSocketMessageWriter {
    timeoutIds: Map<string, NodeJS.Timeout> = new Map();
    pendingDidChangeRequest?: DidChangeRequestMessage;

    /**
     * Overloaded method debouncing specific requests to the language server
     *
     * @param msg The request message
     */
    async write(msg: RequestMessage): Promise<void> {
        switch (msg.method) {
            case 'textDocument/documentLink': // Disable because of AST error in Clang
                return;
            case 'textDocument/codeLens':
                this.debounce(msg, 500);
                return;
            case 'textDocument/hover':
                this.debounce(msg, 500);
                return;
            case 'textDocument/didChange':
                this.handleDidChange(msg as DidChangeRequestMessage, 1000);
                return;
            case 'textDocument/completion':
                this.debounce(msg, 500);
                return;
            case 'textDocument/codeAction':
                this.debounce(msg, 500);
                return;
            default:
                this.send(msg);
                return;
        }
    }

    /**
     * Sends request message through the socket
     * @param msg
     */
    private send(msg: RequestMessage): void {
        try {
            const content = JSON.stringify(msg);
            this.socket.send(content);
        } catch (e) {
            this.errorCount++;
            this.fireError(e, msg, this.errorCount);
        }
    }

    /**
     * Debounces requests using a given delay
     * @param msg The request message
     * @param delay The delay to debounce the requests
     */
    private debounce(msg: RequestMessage, delay: number) {
        const method = msg.method;
        if (this.timeoutIds.get(method)) {
            clearTimeout(this.timeoutIds.get(method));
        }
        this.timeoutIds.set(
            method,
            setTimeout(() => this.send(msg), delay),
        );
    }

    /**
     * Handles change requests by implementing a debounce logic and merging simple change requests
     * together in order to reduce the amount of send data
     * @param msg The change request message
     * @param delay The delay used for debouncing the requests
     */
    private handleDidChange(msg: DidChangeRequestMessage, delay: number) {
        const method = 'textDocument/didChange';

        if (this.timeoutIds.get(method) && this.pendingDidChangeRequest) {
            clearTimeout(this.timeoutIds.get(method));
            this.pendingDidChangeRequest = this.mergeDidChangeRequest(msg);
            this.timeoutIds.set(
                method,
                setTimeout(() => {
                    this.send(this.pendingDidChangeRequest!);
                    this.pendingDidChangeRequest = undefined;
                }, delay),
            );
        } else {
            this.pendingDidChangeRequest = msg;
            this.timeoutIds.set(
                method,
                setTimeout(() => {
                    this.send(this.pendingDidChangeRequest!);
                    this.pendingDidChangeRequest = undefined;
                }, delay),
            );
        }
    }

    /**
     * Merges a new change request to the pending one, if existing.
     * Otherwise, the new message becomes the pending request.
     *
     * Requests are appended to the content changes list, or
     * if the change request represents the writing of a single character, this is merged in the pending one.
     *
     * @param msg The recent change request message
     * @return The merged pending change request
     */
    private mergeDidChangeRequest(msg: DidChangeRequestMessage): DidChangeRequestMessage {
        if (msg.method.localeCompare('textDocument/didChange') !== 0) {
            return msg;
        }
        if (!this.pendingDidChangeRequest) {
            this.pendingDidChangeRequest = msg;
            return msg;
        }

        const newContentChanges = msg.params.contentChanges.first()!;
        const pendingContentChanges = this.pendingDidChangeRequest.params.contentChanges;

        if (this.isSimpleCharWrite(msg)) {
            this.pendingDidChangeRequest.params.contentChanges.last()!.text = pendingContentChanges.last()!.text + newContentChanges.text;
        } else {
            this.pendingDidChangeRequest.params.contentChanges.push(msg.params.contentChanges.first()!);
        }
        return this.pendingDidChangeRequest;
    }

    /**
     * Checks if a change request message represents the action of writing an individual character or newline.
     *
     * @param msg The change request message
     */
    private isSimpleCharWrite(msg: DidChangeRequestMessage): boolean {
        const msgContentChange = msg.params.contentChanges.first()!;
        const pendingContentChange = this.pendingDidChangeRequest?.params.contentChanges.last();

        if (msgContentChange.text.startsWith('\n') || msgContentChange.text.startsWith('\r\n')) {
            return (
                msgContentChange.rangeLength === 0 &&
                msgContentChange.range.start.line === msgContentChange.range.end.line &&
                msgContentChange.range.start.character === msgContentChange.range.end.character &&
                (!pendingContentChange || msgContentChange.range.start.character === pendingContentChange.range.start.character)
            );
        } else {
            return (
                msgContentChange.rangeLength === 0 &&
                msgContentChange.text.length === 1 &&
                msgContentChange.range.start.line === msgContentChange.range.end.line &&
                msgContentChange.range.start.character === msgContentChange.range.end.character &&
                (!pendingContentChange || msgContentChange.range.start.line === pendingContentChange.range.start.line)
            );
        }
    }
}
