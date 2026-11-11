import * as StompJs from "@stomp/stompjs";
import { Product, RequestReport } from "../_types/type";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import {
  getAccessToken,
  setAccessToken,
} from "@/app/my_page/_utils/auth-header";
import { ProductRespons } from "../[id]/_hooks/useProduct";

export const getProductById = async (id: number): Promise<ProductRespons> => {
  return await fetch(`/products/${id}`, {
    headers: {
      Authorization: getAccessToken(),
    },
    cache: "no-cache",
  }).then(async (res) => {
    setAccessToken(res);
    if (res.status === 404) {
      return { status: res.status, data: undefined };
    } else {
      return {
        status: res.status,
        data: (await res.json()) as unknown as Product,
      };
    }
  });
};
export const sendPublicChatMessage = ({
  client,
  productId,
  message,
  isImage,
  userId,
}: {
  client: StompJs.Client;
  productId: number;
  message: string;
  isImage?: boolean;
  userId: number;
}) => {
  client.publish({
    destination: `/chats/${productId}`,
    body: JSON.stringify({
      message,
      userId,
      isImage,
    }),
  });
};

dayjs.extend(utc);

export const getLocalTime = (time: string) => {
  let utcTime = dayjs.utc(time);

  return utcTime.local().format("HH:mm");
};

export const findPrivateChatRoomByProductIdAndBuyerId = async (
  productId: number,
  buyerId: number
) => {
  return await fetch(`/private-chats/find/${productId}/${buyerId}`, {
    headers: {
      Authorization: getAccessToken(),
    },
  }).then(async (res) => {
    setAccessToken(res);
    if (res.status === 404) {
      return -1;
    }
    if (!res.ok) throw new Error("채팅방을 찾지 못했습니다.");
    const roomId: unknown = await res.json();
    if (typeof roomId !== "number" || !Number.isInteger(roomId) || roomId <= 0) {
      throw new Error("채팅방 정보가 올바르지 않습니다.");
    }
    return roomId;
  });
};
export const createPrivateChatRoom = async ({
  buyerId,
  productId,
  sellerId,
}: {
  productId: number;
  buyerId: number;
  sellerId: number;
}) => {
  return await fetch(`/private-chats`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: getAccessToken(),
    },
    body: JSON.stringify({
      productId,
      buyerId,
      sellerId,
    }),
  }).then(async (res) => {
    setAccessToken(res);
    if (!res.ok) throw new Error("채팅방을 생성하지 못했습니다.");
    const roomId: unknown = await res.json();
    if (typeof roomId !== "number" || !Number.isInteger(roomId) || roomId <= 0) {
      throw new Error("채팅방 정보가 올바르지 않습니다.");
    }
    return roomId;
  });
};

export type ReportCreationResult =
  | { status: "created"; reportId: number }
  | { status: "rejected" }
  | { status: "unknown" }
  | { status: "image-failed" | "image-unknown"; reportId: number };

export const createReport = async (
  requestReport: RequestReport
): Promise<ReportCreationResult> => {
  let res: Response;
  try {
    res = await fetch(`/api/reports`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: getAccessToken(),
      },
      body: JSON.stringify(requestReport),
    });
    setAccessToken(res);
  } catch {
    return { status: "unknown" };
  }
  if (!res.ok) {
    return { status: res.status >= 400 && res.status < 500 ? "rejected" : "unknown" };
  }

  let reportId: number;
  try {
    const body: unknown = await res.json();
    if (typeof body !== "object" || body === null || !("reportId" in body)
      || typeof body.reportId !== "number" || !Number.isSafeInteger(body.reportId) || body.reportId <= 0) {
      return { status: "unknown" };
    }
    reportId = body.reportId;
  } catch {
    return { status: "unknown" };
  }
  if (requestReport.images.length > 0) {
    try {
      const images = await createImages(String(reportId), requestReport.images);
      if (!images.ok) {
        return { status: images.status >= 400 && images.status < 500 ? "image-failed" : "image-unknown", reportId };
      }
    } catch {
      return { status: "image-unknown", reportId };
    }
  }
  return { status: "created", reportId };
};

const createImages = async (
  reportId: string,
  images: {
    id: number;
    imageURL: File;
  }[]
) => {
  const fomData = new FormData();

  images.forEach((image) => {
    fomData.append("files", image.imageURL);
  });
  fomData.append("reportId", reportId);

  return await fetch(`/api/reportImages/upload`, {
    method: "POST",
    headers: {
      Authorization: getAccessToken(),
    },
    body: fomData,
  }).then((res) => {
    setAccessToken(res);
    return res;
  });
};
