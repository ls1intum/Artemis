export class DockerConfiguration {
    image: string;
    tag?: string;
    volumes: Map<string, string>;
    parameters: Map<string, string>;
}
