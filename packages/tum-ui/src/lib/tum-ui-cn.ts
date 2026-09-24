import { createCn } from 'cn/engine';
import tables from 'cn/tables';

export const tumUiCn: ReturnType<typeof createCn> = createCn(tables, undefined, { prefix: 'tum' });
