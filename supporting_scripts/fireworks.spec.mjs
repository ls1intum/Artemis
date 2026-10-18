import { fileURLToPath } from 'node:url';
import { compile, Logger } from 'sass';
import { expect, it } from 'vitest';

it('generates the same firework particles for repeated client builds', () => {
    const source = fileURLToPath(new URL('../src/main/webapp/app/atlas/overview/fireworks/fireworks.component.scss', import.meta.url));
    const first = compile(source, { logger: Logger.silent }).css;
    const second = compile(source, { logger: Logger.silent }).css;
    expect(first).toContain('box-shadow:');
    expect(second).toBe(first);
});
