import React, { useEffect, useState } from "react";
import ProductCard from "../_design/ProductCard";

import { createPortal } from "react-dom";
import ModalLarge from "../_design/ModalLarge";

import BuyProductDetails from "./modal/BuyProductDetails";

type Props = {
  buyList: TransactionDetails[];
};

export default function BuyHistory({ buyList }: Props) {
  const [isOpenProductModal, setIsOpenModal] = useState<boolean>(false);

  const [selectedTransactionId, setSelectedTransactionId] = useState<number>();
  const selectedTransaction = buyList.find((buy) => buy.id === selectedTransactionId);

  useEffect(() => {
    if (isOpenProductModal && !selectedTransaction) {
      setIsOpenModal(false);
      setSelectedTransactionId(undefined);
      alert("조회한 목록에서 거래를 찾을 수 없어 상세 화면을 닫았습니다. 거래와 포인트를 확인해 주세요.");
    }
  }, [isOpenProductModal, selectedTransaction]);

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 p-2 gap-2 md:grid-cols-4">
      {buyList.length > 0 ? (
        buyList.map((buy) => (
          <div
            key={buy.id}
            className="cursor-pointer hover:scale-110 transition-all"
            onClick={() => {
              setIsOpenModal(true);
              setSelectedTransactionId(buy.id);
            }}
          >
            <ProductCard
              imageSrc={buy.product.thumbnail.thumbnailURL}
              title={buy.product.title}
              status={buy.status.status}
              price={buy.product.price}
            />
          </div>
        ))
      ) : (
        <div>구매한 물품이 없습니다.</div>
      )}
      {isOpenProductModal && selectedTransaction &&
        createPortal(
          <ModalLarge
            title={selectedTransaction.product.title}
            onClose={() => setIsOpenModal(false)}
          >
            <BuyProductDetails
              transactionDetails={selectedTransaction}
              // chnageStatus={handleChangeTransactionStatus}
            />
          </ModalLarge>,
          document.body
        )}
    </div>
  );
}
