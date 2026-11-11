import { setAccessToken } from "@/app/my_page/_utils/auth-header";
import { Product } from "../[id]/page";
import { getAccessToken } from "../_utils/auth-header";

export const getProductById = async (id: number) => {
  const res = await fetch(`/products/${id}`);
  return res.json();
};

const mapProductToAPISepc = (product: Product) => {
  // TODO 관리자가 수정
  const userId = 1;

  return {
    userId,
    title: product.title,
    content: product.content,
    price: product.price,
    isEscrow: product.isEscrow,
    discountable: product.discountable,
    forSale: product.forSale,
    visible: product.visible,
    purchaseAt: product.purchaseAt,
    freeShipping: product.freeShipping,
    statusId: product.status.id,
    categoryId: product.category.id,
    tags: product.tags,
  };
};

export const createProduct = async (product: Product): Promise<boolean> => {
  try {
    const res = await fetch(`/products/${product.id}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        Authorization: getAccessToken(),
      },
      body: JSON.stringify(mapProductToAPISepc(product)),
    });
    setAccessToken(res);
    if (res.status === 401) {
      alert("유효하지 않은 인증 정보입니다.");
      return false;
    }
    if (!res.ok) return false;
    const body: unknown = await res.json();
    if (typeof body !== "object" || body === null || !("id" in body)
      || typeof body.id !== "number" || !Number.isSafeInteger(body.id) || body.id <= 0 || body.id !== product.id) return false;
    const productId = String(body.id);
    if (product.images.length > 0) {
      const resImages = await createImages(productId, product.images);
      return resImages.ok;
    }
    return true;
  } catch {
    return false;
  }
};

export const deleteImageById = async (productId: number, imageURL: string) => {
  const deleteData = {
    imageURL: imageURL,
  };

  const response = await fetch(`/api/deleteImage/${productId}`, {
    method: "DELETE",
    headers: {
      "Content-Type": "application/json",
      Authorization: getAccessToken(),
    },
    body: JSON.stringify(deleteData),
  });
  setAccessToken(response);
  if (response.status === 401) {
    alert("유효하지 않은 인증 정보입니다.");

    return;
  }

  return response.ok;
};

export const deleteImages = async (productId: number) => {
  const response = await fetch(`/api/deleteImage/${productId}`, {
    method: "DELETE",
    headers: {
      "Content-Type": "application/json",
      Authorization: getAccessToken(),
    },
  });

  if (response.status === 401) {
    alert("유효하지 않은 인증 정보입니다.");

    return;
  }

  setAccessToken(response);

  return response.ok;
};

const createImages = async (
  productId: string,
  images: {
    id: number;
    imageURL: File;
  }[]
) => {
  const fomData = new FormData();

  images.forEach((image) => {
    fomData.append("files", image.imageURL);
  });
  fomData.append("productId", productId);

  // console.log(fomData);

  // return await fetch("/api/upload", {
  const response = await fetch(`/api/upload`, {
    method: "POST",
    headers: {
      Authorization: getAccessToken(),
    },
    body: fomData,
  });
  setAccessToken(response);
  return response;
};
