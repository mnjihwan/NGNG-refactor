"use client";
import { createContext, useContext, useEffect, useState } from "react";
import { listenChatAuthChanges, notifyChatAuthChanged } from "./chatAuth";
export type User = {
  id: number;
  name: string;
  nickname: string;
};
export const UserContext = createContext<{
  getUser: () => User | undefined;
  setUser: (info: User) => void;
  logout: () => void;
  getAccessToken: () => string | undefined;
}>({
  getUser: () => undefined,
  setUser: (info: User) => {},
  logout: () => {},
  getAccessToken: () => undefined,
});
const UserContextProvider = ({ children }: { children: React.ReactNode }) => {
  const getUser = () => {
    const data = sessionStorage.getItem("user");

    if (data) {
      return JSON.parse(data) as User;
    } else {
      return undefined;
    }
  };

  const getAccessToken = () => {
    const token = sessionStorage.getItem("accessToken");
    if (token) {
      return token;
    } else {
      return undefined;
    }
  };
  const setUser = (info: User) => {
    sessionStorage.setItem("user", JSON.stringify(info));
    notifyChatAuthChanged(true);
  };

  const logout = () => {
    const hadSession = sessionStorage.getItem("user") !== null ||
      sessionStorage.getItem("accessToken") !== null;
    sessionStorage.removeItem("user");
    sessionStorage.removeItem("accessToken");
    if (hadSession) notifyChatAuthChanged(true);
  };
  return (
    <UserContext.Provider value={{ getUser, setUser, logout, getAccessToken }}>
      {children}
    </UserContext.Provider>
  );
};
export default UserContextProvider;

// Keep chat connections in sync even when login/logout happens without navigation.
export const useChatUser = () => {
  const { getUser } = useContext(UserContext);
  const [, rerender] = useState(0);
  useEffect(() => {
    const onChange = () => rerender((value) => value + 1);
    return listenChatAuthChanges(onChange);
  }, []);
  return getUser();
};
