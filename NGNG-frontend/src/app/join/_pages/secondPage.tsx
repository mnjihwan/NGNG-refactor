import ColorMode from "@/components/layouts/header/components/ColorMode";
import { useEffect, useRef, useState } from "react";
import { signupPost, signupMessage, signupQuotaRejected, SignupAttempt, SignupChallenge, SignupProof } from "../signupApi";
import UserImage from "../_image/user.svg";
import PhoneImage from "../_image/phone.svg";
import AuthCheck from "../_image/authCheck.svg";
import LogoImage from "../../../components/layouts/header/design/SVG/logo.svg";
import { userInfo } from "../joinPage";
import { useRouter } from "next/navigation";
import Caution from "../caution";

type SecondPageProps = {
  user: userInfo;
  setUser: React.Dispatch<React.SetStateAction<userInfo>>;
  currentPage: number;
  setCurrentPage: React.Dispatch<React.SetStateAction<number>>;
};

export default function SecondPage({
  user,
  setUser,
  currentPage,
  setCurrentPage,
}: SecondPageProps) {
  const router = useRouter();

  const [insertName, setInsertName] = useState(user.name);
  const [insertPhoneNumber, setInsertPhoneNumber] = useState(user.phoneNumber);
  const [checkAuthNumber, setCheckAuthNumber] = useState("");
  const [challenge, setChallenge] = useState<SignupChallenge>();
  const [busy, setBusy] = useState(false);
  const request = useRef(0);
  const pending = useRef(false);
  const alive = useRef(true);
  useEffect(() => { alive.current = true; return () => { alive.current = false; request.current++; }; }, []);
  const isDisabledNextPage = !user.phoneProof || busy;
  const isPhoneNumberInputDisabled = busy;
  const isSendMessage = !challenge;
  const reset = () => {
    request.current++; setChallenge(undefined); setCheckAuthNumber("");
    setUser(previous => ({ ...previous, attempt: undefined, phoneProof: undefined, emailProof: undefined }));
  };
  const leave = (page: number) => { if (pending.current) return; reset(); setCurrentPage(page); };
  const sendMessage = async () => {
    if (pending.current || !insertName || !insertPhoneNumber) return;
    pending.current = true; setBusy(true); const sequence = ++request.current;
    try {
      const attempt = user.attempt ?? await signupPost<SignupAttempt>("/auth/attempt", {}, 201);
      if (!alive.current || sequence !== request.current) return;
      if (!attempt.attemptId || !attempt.attemptSecret) throw new Error("인증을 다시 시작하세요.");
      setUser(previous => ({ ...previous, attempt }));
      const next = await signupPost<SignupChallenge>("/auth/phonenumber", { name: insertName, phoneNumber: insertPhoneNumber, attemptId: attempt.attemptId, attemptSecret: attempt.attemptSecret });
      if (!alive.current || sequence !== request.current) return;
      if (!next.challengeId) throw new Error("인증을 다시 시작하세요.");
      setChallenge(next); setCheckAuthNumber("");
      setUser(previous => ({ ...previous, name: insertName, phoneNumber: insertPhoneNumber, phoneProof: undefined }));
      alert("입력하신 전화번호로 인증번호를 전송했습니다.");
    } catch (error) {
      if (alive.current && sequence === request.current) {
        if (!signupQuotaRejected(error)) {
          setChallenge(undefined); setCheckAuthNumber("");
          setUser(previous => ({ ...previous, phoneProof: undefined }));
        }
        alert(signupMessage(error));
      }
    }
    finally { pending.current = false; if (alive.current) setBusy(false); }
  };
  const authCheck = async () => {
    if (pending.current || !challenge || !user.attempt || !/^[0-9]{6}$/.test(checkAuthNumber)) return;
    pending.current = true; setBusy(true); const sequence = ++request.current;
    try {
      const result = await signupPost<SignupProof>("/auth/phonenumber/verify", { attemptId: user.attempt.attemptId, attemptSecret: user.attempt.attemptSecret, challengeId: challenge.challengeId, code: checkAuthNumber });
      if (!alive.current || sequence !== request.current) return;
      if (!result.proof) throw new Error("인증을 다시 시작하세요.");
      setUser(previous => ({ ...previous, name: insertName, phoneNumber: insertPhoneNumber, phoneProof: result.proof }));
      setCheckAuthNumber(""); alert("인증되었습니다.");
    } catch (error) { if (alive.current && sequence === request.current) alert(signupMessage(error)); }
    finally { pending.current = false; if (alive.current) setBusy(false); }
  };

  return (
    <div className="dark:bg-[#282828]">
      <div className="absolute right-12 mt-6">
        <ColorMode />
      </div>
      <div className="flex justify-center items-center h-screen">
        <div className="flex-col justify-center items-center w-5/6 md:w-1/4 h-auto">
          <button type="button" className="flex justify-center w-full" onClick={() => router.push("/")}>
            <LogoImage className="w-1/2" />
          </button>
          <div className="text-sm mt-14">
            <button type="button" onClick={() => leave(1)}>
              약관동의
            </button>
            <span> {">"} </span>
            <button type="button" className="font-bold" disabled>
              본인확인
            </button>
            <span> {">"} </span>
            <button type="button" disabled>
              정보입력
            </button>
            <span> {">"} </span>
            <span>가입완료</span>
          </div>
          <div className="flex-col h-auto mt-2">
            <div className="flex items-center rounded-md border-[1px] dark:bg-[#3B3B3B] border-black/45 w-full h-14 p-2">
              <div className="flex w-[100%]">
                <UserImage className="w-9 mr-4 ml-2 fill-black/50 dark:fill-[#9CA3AF]" />
                <input
                  type="text"
                  maxLength={12}
                  placeholder="이름"
                  value={insertName}
                  onChange={(e) => { reset(); setInsertName(e.target.value); }}
                  disabled={busy}
                  className="w-full focus:outline-none text-xl"
                />
              </div>
            </div>
            <div className="flex justify-between items-center w-full h-14 mt-8">
              <div className="flex justify-center items-center w-[75%] p-2 rounded-md border-[1px] dark:bg-[#3B3B3B] border-black/45 h-14">
                <PhoneImage className="h-10 mr-4 ml-2 fill-black/50 dark:fill-[#9CA3AF]" />
                <input
                  type="tel"
                  maxLength={13}
                  placeholder="전화번호"
                  value={insertPhoneNumber}
                  onChange={e => { reset(); setInsertPhoneNumber(e.target.value); }}
                  disabled={isPhoneNumberInputDisabled}
                  className="w-full focus:outline-none text-xl"
                />
              </div>
              <button
                className="flex justify-center items-center rounded-md text-white bg-point-color w-[20%] h-14 text-xl"
                disabled={busy}
                onClick={sendMessage}
              >
                인증
              </button>
            </div>
            <div
              className={`flex justify-between items-center w-full h-14 mt-2 ${isSendMessage ? "hidden" : ""
                }`}
            >
              <div className="flex justify-center items-center w-[75%] p-2 rounded-md border-[1px] dark:bg-[#3B3B3B] border-black/45 h-14">
                <AuthCheck className="w-9 mr-4 ml-2 fill-black/50 dark:fill-[#9CA3AF]" />
                <input
                  type="text"
                  inputMode="numeric"
                  maxLength={6}
                  placeholder="전화번호 인증"
                  value={checkAuthNumber}
                  onChange={(e) => setCheckAuthNumber(e.target.value)}
                  className="w-full focus:outline-none text-xl"
                />
              </div>
              <button
                className="flex justify-center items-center rounded-md text-white bg-point-color w-[20%] h-14 text-xl"
                disabled={busy || !!user.phoneProof}
                onClick={authCheck}
              >
                확인
              </button>
            </div>
            <button type="button" disabled={busy} onClick={reset} className="mt-4 underline">인증 처음부터 다시 시작</button>
            <div className="flex justify-between mt-12">
              <button
                className="border-solid rounded-md dark:text-white dark:bg-[#3B3B3B] dark:border-black/45 text-black bg-gray-100 border-[2px] w-[calc(50%-1vw)] h-16 text-3xl"
                onClick={() => leave(currentPage - 1)}
              >
                이전
              </button>
              <button
                className="border-solid rounded-md text-white bg-point-color w-[calc(50%-1vw)] h-16 text-3xl"
                onClick={() => setCurrentPage(currentPage + 1)}
                disabled={isDisabledNextPage}
              >
                다음
              </button>
            </div>
          </div>
          <div className="flex justify-center">
            <Caution />
          </div>
        </div>
      </div>
    </div>
  );
}
