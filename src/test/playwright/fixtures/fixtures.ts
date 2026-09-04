import { promises as fs } from 'fs';

export class Fixtures {
    static async get(filePath: string, encoding: BufferEncoding = 'utf-8'): Promise<string | undefined> {
        try {
            const fullPath = `${__dirname}/${filePath}`;
            return await fs.readFile(fullPath, encoding);
        } catch (error) {
            console.error(`Error reading fixture file: ${error instanceof Error ? error.message : error}`);
            return undefined;
        }
    }

    static getAbsoluteFilePath(filePath: string) {
        return `${__dirname}/${filePath}`;
    }
}
