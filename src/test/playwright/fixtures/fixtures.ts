import { promises as fs } from 'fs';

export class Fixtures {
    static async get(filePath: string, encoding: BufferEncoding = 'utf-8') {
        try {
            const fullPath = `${__dirname}/${filePath}`;
            return await fs.readFile(fullPath, encoding);
        } catch (error) {
            console.error(`Error reading fixture file: ${error.message}`);
        }
    }

    static getAbsoluteFilePath(filePath: string) {
        return `${__dirname}/${filePath}`;
    }
}
