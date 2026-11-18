import { promises as fs } from 'fs';

export class Fixtures {
    static async get(filePath: string, encoding: BufferEncoding = 'utf-8'): Promise<string> {
        const fullPath = `${__dirname}/${filePath}`;
        try {
            return await fs.readFile(fullPath, encoding);
        } catch (error) {
            // A missing fixture is a broken test setup, and every caller goes straight on to use the content. Handing
            // back nothing only moved the failure to whatever consumed it, where it reads as an unrelated defect.
            throw new Error(`Could not read the fixture ${fullPath}`, { cause: error });
        }
    }

    static getAbsoluteFilePath(filePath: string) {
        return `${__dirname}/${filePath}`;
    }
}
