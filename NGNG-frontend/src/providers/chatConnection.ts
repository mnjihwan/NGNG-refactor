import { Client } from "@stomp/stompjs";
import {
  listenChatAuthChanges,
  getChatAuthorization,
  getChatUserId,
} from "./chatAuth";

// Authentication belongs to each CONNECT, including automatic network reconnects.
export const createChatConnection = ({
  userId,
  requiresAuth = false,
  onConnect,
  onUnavailable,
}: {
  userId?: number;
  requiresAuth?: boolean;
  onConnect: (client: Client) => void;
  onUnavailable: () => void;
}) => {
  let disposed = false;
  let authorization: string | undefined;
  let ready = false;
  let restarting: Promise<void> | undefined;
  const client = new Client({
    brokerURL: `${process.env.NEXT_PUBLIC_CHAT_SOCKET}/chat-server`,
    reconnectDelay: 5000,
    // STOMP's default diagnostics may include protocol details; never log frames.
    debug: () => {},
  });
  // Logout and credential changes must not wait for a DISCONNECT receipt.
  const deactivate = () => client.deactivate({ force: true });
  const identityMatches = () => getChatUserId() === userId;
  client.beforeConnect = async () => {
    ready = false;
    authorization = getChatAuthorization();
    if (disposed || !identityMatches() || (requiresAuth && !authorization)) {
      if (!disposed) onUnavailable();
      void deactivate();
      return;
    }
    client.connectHeaders = authorization ? { Authorization: authorization } : {};
  };
  client.onConnect = () => {
    if (disposed || !client.active || !identityMatches()) {
      void deactivate();
      return;
    }
    ready = true;
    onConnect(client);
  };
  client.onStompError = () => {
    ready = false;
    if (disposed) return;
    onUnavailable();
    // Do not repeatedly retry a rejected credential or fall back to anonymous.
    // A later HTTP token update can restart with the new access token.
    void deactivate();
  };
  client.onWebSocketClose = () => {
    ready = false;
    if (!disposed) onUnavailable();
  };
  client.onWebSocketError = () => {
    ready = false;
    if (!disposed) onUnavailable();
  };
  const onAuthChanged = () => {
    if (disposed) return;
    if (!identityMatches()) {
      ready = false;
      onUnavailable();
      void deactivate();
      return;
    }
    if (authorization === getChatAuthorization() || restarting) return;
    ready = false;
    onUnavailable();
    restarting = deactivate().then(() => {
      if (!disposed && identityMatches()) client.activate();
    }).finally(() => { restarting = undefined; });
  };
  const stopListening = listenChatAuthChanges(onAuthChanged);
  return {
    client,
    isCurrent: () => !disposed && ready && identityMatches() &&
      authorization === getChatAuthorization(),
    dispose: () => {
      disposed = true;
      ready = false;
      stopListening();
      void deactivate();
    },
  };
};
