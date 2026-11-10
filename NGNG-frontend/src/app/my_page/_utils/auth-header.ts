import { jwtDecode } from "jwt-decode";
import { notifyChatAuthChanged } from "@/providers/chatAuth";

export const getAccessToken = () => {
  if (typeof window !== "undefined") {
    let token = sessionStorage.getItem("accessToken")?.replaceAll('"', "");

    return token !== undefined ? `${token}` : "";
  } else {
    return "";
  }
};

export const setAccessToken = (response: Response) => {
  const token = response.headers.get("Authorization");

  if (token !== null) {
    const previous = sessionStorage.getItem("accessToken");
    sessionStorage.setItem("accessToken", JSON.stringify(token));
    const roleToken = getAccessToken();

    document.cookie = `roleToken=${roleToken}`;
    if (previous !== JSON.stringify(token)) notifyChatAuthChanged();
  }
};
