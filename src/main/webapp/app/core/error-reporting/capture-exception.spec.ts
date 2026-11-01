import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { VERSION } from 'app/app.constants';
import { ClientErrorReport, ClientErrorReporter, captureException } from 'app/core/error-reporting/capture-exception';

describe('ClientErrorReporter', () => {
    let send: ReturnType<typeof vi.fn<(report: ClientErrorReport) => void>>;
    let time: number;
    let reporter: ClientErrorReporter;

    const reportOf = (index: number): ClientErrorReport => send.mock.calls[index][0];
    // the same line, so that two errors have the same stack, as one error that is thrown twice from the same place
    const boom = () => new Error('boom');

    beforeEach(() => {
        send = vi.fn();
        time = 1_000_000;
        reporter = new ClientErrorReporter(send, () => time);
    });

    describe('building a report', () => {
        it('should report an error with its message, stack, page and version', () => {
            const error = new Error('Unknown question type');

            reporter.report(error);

            expect(send).toHaveBeenCalledOnce();
            expect(reportOf(0)).toEqual({
                severity: 'ERROR',
                message: 'Unknown question type',
                stack: error.stack,
                url: window.location.pathname,
                clientVersion: VERSION,
                extra: undefined,
                tags: undefined,
            });
        });

        it('should use the name of an error without a message', () => {
            const error = new Error('');
            error.name = 'QuotaExceededError';

            reporter.report(error);

            expect(reportOf(0).message).toBe('QuotaExceededError');
        });

        it('should report a message without a stack', () => {
            reporter.report('Lecture has no course id: 7');

            expect(reportOf(0).message).toBe('Lecture has no course id: 7');
            expect(reportOf(0).stack).toBeUndefined();
        });

        it('should describe a value that is neither an error nor a message', () => {
            reporter.report({ status: 500, detail: 'broken' });

            expect(reportOf(0).message).toBe('{"status":500,"detail":"broken"}');
        });

        it('should describe a value that cannot be converted to json', () => {
            const circular: Record<string, unknown> = {};
            circular['self'] = circular;

            reporter.report(circular);

            expect(reportOf(0).message).toBe('[object Object]');
        });

        it('should name a report without any text', () => {
            reporter.report('');

            expect(reportOf(0).message).toBe('Unknown error');
        });

        it('should append the causes of an error to its stack', () => {
            const root = new Error('connection reset');
            const error = new Error('Failed to download build logs', { cause: new Error('middle', { cause: root }) });

            reporter.report(error);

            const stack = reportOf(0).stack!;
            expect(stack).toContain('Failed to download build logs');
            expect(stack).toContain('Caused by: Error: middle');
            expect(stack).toContain('Caused by: Error: connection reset');
        });

        it('should append a cause that is not an error', () => {
            reporter.report(new Error('wrapped', { cause: { status: 503 } }));

            expect(reportOf(0).stack).toContain('Caused by: {"status":503}');
        });

        it('should use the severity of the context', () => {
            reporter.report(new Error('only a hint'), { severity: 'INFO' });

            expect(reportOf(0).severity).toBe('INFO');
        });

        it('should send the extra information and the tags as text', () => {
            reporter.report(new Error('boom'), { extra: { exerciseId: 42, exercise: { id: 42, title: 'Quiz' }, skipped: undefined, flag: false }, tags: { feature: 'quiz' } });

            expect(reportOf(0).extra).toEqual({ exerciseId: '42', exercise: '{"id":42,"title":"Quiz"}', flag: 'false' });
            expect(reportOf(0).tags).toEqual({ feature: 'quiz' });
        });

        it('should not send an empty context', () => {
            reporter.report(new Error('boom'), { extra: { skipped: undefined }, tags: {} });

            expect(reportOf(0).extra).toBeUndefined();
            expect(reportOf(0).tags).toBeUndefined();
        });

        it('should convert extra information that cannot be converted to json', () => {
            const circular: Record<string, unknown> = {};
            circular['self'] = circular;

            reporter.report(new Error('boom'), { extra: { circular } });

            expect(reportOf(0).extra).toEqual({ circular: '[object Object]' });
        });

        it('should keep every value within what the server accepts', () => {
            const long = 'x'.repeat(20_000);
            const manyEntries = Object.fromEntries(Array.from({ length: 25 }, (_, index) => [`key${index}${long}`, long]));

            reporter.report(new Error(long, { cause: new Error(long) }), { extra: manyEntries, tags: manyEntries });

            const report = reportOf(0);
            expect(report.message).toHaveLength(1000);
            expect(report.stack!.length).toBeLessThanOrEqual(8000);
            expect(Object.keys(report.extra!)).toHaveLength(10);
            expect(Object.keys(report.extra!).every((key) => key.length <= 50)).toBe(true);
            expect(Object.values(report.extra!).every((value) => value.length <= 500)).toBe(true);
            expect(Object.keys(report.tags!)).toHaveLength(10);
        });
    });

    describe('limiting reports', () => {
        it('should send the same error only once per minute', () => {
            reporter.report(boom());
            time += 59_000;
            reporter.report(boom());

            expect(send).toHaveBeenCalledOnce();
        });

        it('should send the same error again after a minute', () => {
            reporter.report(boom());
            time += 60_000;
            reporter.report(boom());

            expect(send).toHaveBeenCalledTimes(2);
        });

        it('should not count a duplicate as a new report of the page', () => {
            // one error reported for a long time keeps the same slot, so it cannot use up the budget of the page
            for (let report = 0; report < 100; report++) {
                reporter.report(boom());
            }
            reporter.report(new Error('another error'));

            expect(send).toHaveBeenCalledTimes(2);
        });

        it('should treat errors that differ in severity or message as different errors', () => {
            reporter.report(boom());
            reporter.report(boom(), { severity: 'WARN' });
            reporter.report(new Error('bang'));

            expect(send).toHaveBeenCalledTimes(3);
        });

        it('should send at most ten reports per minute', () => {
            for (let report = 0; report < 25; report++) {
                reporter.report(new Error(`error ${report}`));
            }

            expect(send).toHaveBeenCalledTimes(10);
        });

        it('should send again once the reports of the last minute are old enough', () => {
            for (let report = 0; report < 10; report++) {
                reporter.report(new Error(`error ${report}`));
            }
            time += 60_000;
            reporter.report(new Error('error after a minute'));

            expect(send).toHaveBeenCalledTimes(11);
        });
    });

    it('should never throw, even if sending fails', () => {
        send.mockImplementation(() => {
            throw new Error('network down');
        });

        expect(() => reporter.report(new Error('boom'))).not.toThrow();
    });

    it('should never throw, even if the error cannot be read', () => {
        const hostile = new Proxy(new Error('boom'), {
            get: () => {
                throw new Error('cannot read');
            },
        });

        expect(() => reporter.report(hostile)).not.toThrow();
    });
});

describe('captureException', () => {
    let fetchSpy: ReturnType<typeof vi.fn>;
    let originalFetch: typeof window.fetch | undefined;

    beforeEach(() => {
        originalFetch = window.fetch;
        fetchSpy = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));
        window.fetch = fetchSpy as unknown as typeof window.fetch;
    });

    afterEach(() => {
        window.fetch = originalFetch as typeof window.fetch;
    });

    it('should post the report to the public endpoint of the server', () => {
        captureException(new Error('posted to the server'), { tags: { feature: 'posting' } });

        expect(fetchSpy).toHaveBeenCalledOnce();
        const [url, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
        expect(url).toBe('api/core/public/client-errors');
        expect(init).toMatchObject({ method: 'POST', headers: { 'Content-Type': 'application/json' }, credentials: 'same-origin', keepalive: true });
        expect(JSON.parse(init.body as string)).toMatchObject({ severity: 'ERROR', message: 'posted to the server', clientVersion: VERSION, tags: { feature: 'posting' } });
    });

    it('should not fail if the server cannot be reached', async () => {
        fetchSpy.mockRejectedValue(new TypeError('Failed to fetch'));
        const unhandled = vi.fn();
        process.on('unhandledRejection', unhandled);

        expect(() => captureException(new Error('server is down'))).not.toThrow();
        await new Promise((resolve) => setTimeout(resolve));

        process.off('unhandledRejection', unhandled);
        expect(unhandled).not.toHaveBeenCalled();
    });

    it('should do nothing if the browser has no fetch', () => {
        window.fetch = undefined as unknown as typeof window.fetch;

        expect(() => captureException(new Error('no fetch available'))).not.toThrow();
    });
});
