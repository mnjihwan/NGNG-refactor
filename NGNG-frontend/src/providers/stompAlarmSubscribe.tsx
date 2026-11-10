"use client";

import { useEffect, useRef } from "react";
import { useChatUser } from "./UserContext";
import { createChatConnection } from "./chatConnection";
import { mutate } from "swr";

export default function StompAlarmSubscibe() {
  const user = useChatUser();
  const notificationRef = useRef<Notification>();

  useEffect(() => {
    if (!user) return;
    const chatAlarm = new Set<number>();
    if (typeof Notification !== "undefined" && Notification.permission !== "granted") {
      try {
        void Promise.resolve(Notification.requestPermission()).catch(() => {});
      } catch {
        // Notification permission does not determine chat authentication.
      }
    }
    const connection = createChatConnection({
      userId: user.id,
      requiresAuth: true,
      onUnavailable: () => notificationRef.current?.close(),
      onConnect: (client) => {
        client.subscribe(`/alarms/${user.id}`, (message) => {
          if (!connection.isCurrent()) return;
          try {
            const data = JSON.parse(message.body).body;
            if (typeof data?.chatId !== "number" || typeof data?.message !== "string" ||
              typeof data?.user?.nickname !== "string" || chatAlarm.has(data.chatId)) return;
            chatAlarm.add(data.chatId);
            void mutate("/chat/private-chat/user-id");
            if (typeof Notification === "undefined" || Notification.permission !== "granted") return;
            notificationRef.current = new Notification(data.user.nickname, {
              body: data.message,
              icon: data.productThumbnail,
              tag: String(data.chatId),
              dir: "ltr",
              lang: "ko",
              requireInteraction: false,
            });
            notificationRef.current.onclick = (e) => {
              e.preventDefault();
              window.focus();
              notificationRef.current?.close();
            };
          } catch {
            // ERROR frames and malformed bodies must not become notifications.
          }
        });
      },
    });
    connection.client.activate();
    return () => {
      connection.dispose();
      notificationRef.current?.close();
    };
  }, [user?.id]);

  return (
    <div
      id="alarm"
      className="p-2 list-none max-w-[24rem] cursor-pointer absolute right-0 z-50 mt-[4rem] md:mt-2"
    />
  );
}
