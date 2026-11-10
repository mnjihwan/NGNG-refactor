"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Message from "@/components/common/chat/Message";
import * as StompJs from "@stomp/stompjs";
import { sendPublicChatMessage } from "../../_api/api";
import { Chat, Product } from "../../_types/type";
import { useChatUser } from "@/providers/UserContext";
import { createChatConnection } from "@/providers/chatConnection";
import ChattingHeader from "./ChattingHeader";
import ChattingInputTextArea from "./ChattingInputTextArea";

type ChattingProps = {
  data: Product;
};
export default function Chatting({ data }: ChattingProps) {
  const user = useChatUser();

  const recentChatRef = useRef<HTMLDivElement>(null);

  const [stompClient, setStompClient] = useState<StompJs.Client | null>(null);
  const [chatData, setChatData] = useState<Chat[]>(data.chats);
  const [message, setMessage] = useState<string>("");
  const [image, setImage] = useState<File | undefined>(undefined);
  const [connectionError, setConnectionError] = useState(false);
  // if (user === undefined) {
  //   redirect("/login");
  // }
  const isReported =
    data.reports === null
      ? false
      : data.reports.filter((report) => report.isReport).length > 0;

  const enter = () => {
    if (image !== undefined) {
      sendImage(image);
      setImage(undefined);
    } else {
      sendMessage(message);
    }
  };
  const changeMessage = (message: string) => {
    setMessage(message);
  };
  const sendImage = (image: File) => {
    if (!user || !stompClient?.active || !stompClient.connected || connectionError) return;
    // 이미지 서버에 전송하기
    user !== undefined &&
      stompClient &&
      sendPublicChatMessage({
        client: stompClient,
        message: URL.createObjectURL(image),
        productId: data.id,
        isImage: true,
        userId: user.id,
      });

    // Display the server's message only; a rejected SEND is not a successful echo.
    setMessage("");
    setImage(undefined);
    scrollToBottom();
  };

  const scrollToBottom = useCallback(() => {
    if (recentChatRef.current) {
      recentChatRef.current.scrollTop = recentChatRef.current.scrollHeight;
    }
  }, []);

  const sendMessage = (message: string) => {
    if (!user || !stompClient?.active || !stompClient.connected || connectionError) return;
    user !== undefined &&
      stompClient &&
      sendPublicChatMessage({
        client: stompClient,
        message,
        productId: data.id,
        userId: user.id,
      });
    setMessage("");
    scrollToBottom();
  };

  const resetImage = () => {
    setImage(undefined);
    setMessage("");
  };

  const changeFile = (file: File) => {
    setImage(file);
    setMessage(" ");
  };

  const enterMessage = () => {
    if (image !== undefined) {
      sendImage(image);
      setImage(undefined);
    } else {
      sendMessage(message);
    }
  };
  // console.log("data", chatData);
  const getMessage = (data: any) => {
    // console.log("메시지 수신 데이터들", chatData);
    // console.log("메시지 수신", data);
    setChatData((prev) => [
      ...prev,
      {
        id: data.id,
        message: data.message,
        userId: data.userId,
        userName: data.userName,
        userNickName: data.userNickname,
        createdAt: data.createdAt,
      },
    ]);
  };

  useEffect(() => {
    if (isReported) return;
    const connection = createChatConnection({
      userId: user?.id,
      onUnavailable: () => setConnectionError(true),
      onConnect: (client) => {
        setConnectionError(false);
        client.subscribe(`/public-chats/${data.id}`, (message) => {
          if (!connection.isCurrent()) return;
          try {
            const body = JSON.parse(message.body).body;
            if (typeof body?.id !== "number" || typeof body?.message !== "string" ||
              typeof body?.userId !== "number") {
              setConnectionError(true);
              return;
            }
            getMessage(body);
          } catch {
            setConnectionError(true);
          }
        });
      },
    });
    setStompClient(connection.client);
    connection.client.activate();
    return connection.dispose;
  }, [user?.id, data.id, isReported]);

  useEffect(() => {
    setChatData(data.chats);
  }, [data]);

  useEffect(() => {
    setTimeout(() => {
      scrollToBottom();
    }, 200);
  }, [chatData]);

  return (
    <div className="lg:sticky relative w-full lg:w-[30%] dark:lg:shadow-gray-950 lg:shadow-[rgba(0,_0,_0,_0.24)_0px_3px_8px] p-5 h-[calc(100vh-128px)]  lg:top-[128px]  min-w-80 shadow-none dark:bg-bg-black">
      <ChattingHeader data={data} userId={user?.id} />
      {connectionError && <p role="status">채팅 연결을 확인해 주세요. 인증이 만료된 경우 다시 로그인해 주세요.</p>}
      <div
        className="min-w-72 h-[calc(100%-15rem)] mt-3 flex flex-col items-start overflow-y-scroll scrollbar-hide"
        ref={recentChatRef}
      >
        {chatData
          .sort((a: any, b: any) => {
            return a.createdAt - b.createdAt;
          })
          .map((chat, index) => {
            return (
              <Message
                key={chat.id}
                direction={
                  user === undefined
                    ? "left"
                    : user.id === chat.userId
                    ? "right"
                    : "left"
                }
                userName={chat.userNickName}
                text={chat.message}
                isImage={chat.isImage}
                isFirstOfTheDay={
                  index === 0 ||
                  chatData[index - 1].createdAt?.split("T")[0] !==
                    chat.createdAt?.split("T")[0]
                }
                createdAt={chat.createdAt}
              />
            );
          })}
      </div>
      {user !== undefined && (
        <ChattingInputTextArea
          changeFile={changeFile}
          changeMessage={changeMessage}
          enter={enter}
          enterMessage={enterMessage}
          isReported={isReported}
          message={message}
          resetImage={resetImage}
          image={image}
        />
      )}
    </div>
  );
}
