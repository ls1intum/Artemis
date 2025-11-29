export interface WebsocketNode {
    memberId: string;
    address: string;
    host: string;
    port: number;
    local: boolean;
    liteMember: boolean;
    instanceId?: string;
    brokerConnected: boolean;
}
