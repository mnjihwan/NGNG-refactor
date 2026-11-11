import React, { useEffect, useRef, useState } from "react";
import { PRODUCT_STATUS, PRODUCT_STATUS_BUYER } from "../../_constans/enum";
import ProductDetail from "../../_design/ProductDetail";
import useMypageSWR from "../../_hooks/useMypageSWR";
import { isPayment, paymentProduct, PaymentRequestError } from "../../_api/api";

type Props = {
  transactionDetails: TransactionDetails;
};

export default function BuyProductDetails({ transactionDetails }: Props) {
  const { UpdateTransactionDetailStatus, refreshPaymentState } = useMypageSWR();
  const paymentPending = useRef(false);
  const [isPaying, setIsPaying] = useState(false);

  const chnageStatus = (status: number) => {
    UpdateTransactionDetailStatus({
      data: {
        transactionDetailsId: transactionDetails.id,
        status: status + "",
      },
    });
  };

  const onPayment = async () => {
    if (paymentPending.current) return;
    paymentPending.current = true;
    setIsPaying(true);
    const cost = transactionDetails.price;
    let submitted = false;

    const reload = async () => {
      try {
        await refreshPaymentState();
      } catch {
        alert("거래와 포인트 재조회에 실패했습니다. 상태를 확인하기 전 다시 결제하지 마세요.");
      }
    };

    try {
      const isPay = await isPayment(cost);

      if (!isPay) {
        alert("포인트가 부족 합니다.");
        await reload();
        return;
      }

      if (confirm("결제 하는 과정은 스킵 합니다.")) {
        submitted = true;
        await paymentProduct({
          cost,
          productId: transactionDetails.product.id,
        });

        await reload();
      }
    } catch (error) {
      if (error instanceof PaymentRequestError && error.status === 409) {
        alert("결제 요청이 거부되었습니다. 거래와 포인트를 확인해 주세요. 현재 상태만으로 결제 성공을 판단할 수 없습니다.");
      } else if (submitted && (!(error instanceof PaymentRequestError) || error.status >= 500)) {
        alert("결제 응답을 확인하지 못했습니다. 결제 여부가 불명확하므로 상태를 확인하기 전 다시 결제하지 마세요.");
      } else {
        alert(submitted ? "결제 요청이 거부되었습니다. 거래와 포인트를 확인해 주세요." : "포인트 확인에 실패했습니다. 거래와 포인트를 확인해 주세요.");
      }
      await reload();
    } finally {
      paymentPending.current = false;
      setIsPaying(false);
    }
  };

  const handleConfirmation = () => {
    chnageStatus(PRODUCT_STATUS["구매확정"]);
  };

  const onReport = () => {
    // 거래 신고 모달 작업 후 이송
    if (confirm("정말로 신고 하시 겠습니까?.")) {
      chnageStatus(PRODUCT_STATUS["거래 신고"]);
    }
  };

  const onCancel = () => {
    // 거래 취소 모달 작업후 이송
    if (confirm("정말로 취소 하시 겠습니까?.")) {
      if (transactionDetails!.status.id >= PRODUCT_STATUS["입금 완료"]) {
        alert("입금이 되어 취소가 불가능 합니다.");
        return;
      }
      chnageStatus(PRODUCT_STATUS["거래 취소"]);
    }
  };

  const RenderTranscationSatus: { [name: number]: React.JSX.Element } = {
    [PRODUCT_STATUS_BUYER["입금 대기"]]: (
      <div className="flex gap-2">
        <button
          className="border border-green-600 text-green-600 font-bold py-2 px-4 rounded hover:bg-green-600 hover:text-white"
          onClick={onPayment}
          disabled={isPaying}
        >
          결제 하기
        </button>
      </div>
    ),
    [PRODUCT_STATUS["배송완료"]]: (
      <div>
        <button
          className="border border-green-600 text-green-600 font-bold py-2 px-4 rounded hover:bg-green-600 hover:text-white"
          onClick={handleConfirmation}
        >
          구매 확정
        </button>
      </div>
    ),
  };
  useEffect(() => {
    // console.log(transactionDetails);
  }, []);

  return (
    <>
      <div className="flex gap-2 flex-col p-6">
        {/* 버튼 부분 */}
        <div className="flex flex-col gap-2 my-2">
          <div className="flex flex-col gap-2 items-end">
            <>
              {/* 거래가 성사되어 진행 중일떄 */}
              <div className="flex ">
                <span>현재 거래 상태 : </span>
                <span>{transactionDetails?.status?.status}</span>
              </div>
              {/* 상태에 따라 랜더 */}

              {RenderTranscationSatus[transactionDetails.status.id]}
            </>
            <div className="flex gap-4">
              <button
                className="border border-red-500 text-red-500 font-bold py-2 px-4 rounded hover:bg-red-500 hover:text-white"
                onClick={onReport}
              >
                신고
              </button>
              <button
                className="border border-red-500 text-red-500 font-bold py-2 px-4 rounded hover:bg-red-500 hover:text-white"
                onClick={onCancel}
              >
                거래 취소
              </button>
            </div>
          </div>
        </div>
        {/* 제품 정보 렌더 */}
        <ProductDetail product={transactionDetails.product} />
      </div>
    </>
  );
}
