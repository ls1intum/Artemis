export interface WebsocketNode {
    memberId: string;
    address: string;
    host: string;
    port: number;
    local: boolean;
    instanceId?: string;
    brokerConnected: boolean;
}
