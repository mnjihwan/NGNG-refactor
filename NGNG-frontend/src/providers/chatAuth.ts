// Same-tab storage writes do not produce a browser storage event.
export const CHAT_AUTH_CHANGED = "ngng:chat-auth-changed";
let authChannel: BroadcastChannel | undefined;

const getAuthChannel = () => {
  if (!authChannel && typeof window.BroadcastChannel === "function") {
    try {
      authChannel = new window.BroadcastChannel("ngng:chat-auth");
      authChannel.onmessage = (event: MessageEvent) => {
        if (event.data?.type !== "invalidate") return;
        // sessionStorage is copied to chat popups; retire that copy on logout/login.
        sessionStorage.removeItem("user");
        sessionStorage.removeItem("accessToken");
        window.dispatchEvent(new Event(CHAT_AUTH_CHANGED));
      };
    } catch {
      // Same-window cleanup still works if the browser blocks BroadcastChannel.
    }
  }
  return authChannel;
};

export const listenChatAuthChanges = (listener: () => void) => {
  getAuthChannel();
  window.addEventListener(CHAT_AUTH_CHANGED, listener);
  return () => window.removeEventListener(CHAT_AUTH_CHANGED, listener);
};

export const notifyChatAuthChanged = (invalidateOtherWindows = false) => {
  window.dispatchEvent(new Event(CHAT_AUTH_CHANGED));
  // Never send access tokens, user details or chat content between windows.
  if (invalidateOtherWindows) getAuthChannel()?.postMessage({ type: "invalidate" });
};

export const getChatUserId = (): number | undefined => {
  try {
    return JSON.parse(sessionStorage.getItem("user") ?? "null")?.id;
  } catch {
    return undefined;
  }
};

export const getChatAuthorization = (): string | undefined => {
  const stored = sessionStorage.getItem("accessToken");
  if (!stored) return undefined;
  let token = stored;
  try {
    const parsed = JSON.parse(stored);
    if (typeof parsed === "string") token = parsed;
  } catch {
    // Also accept the unquoted form used by existing callers.
  }
  if (!token) return undefined;
  return token.startsWith("Bearer ") ? token : `Bearer ${token}`;
};
