import ColorMode from "@/components/layouts/header/components/ColorMode";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { signupPost, signupMessage, signupQuotaRejected, SignupChallenge, SignupProof } from "../signupApi";
import LogoImage from "../../../components/layouts/header/design/SVG/logo.svg";
import EmailImage from "../_image/email.svg";
import PasswordImage from "../_image/password.svg";
import AuthCheck from "../_image/authCheck.svg";
import UserImage from "../_image/user.svg";
import { userInfo } from "../joinPage";
import Caution from "../caution";

type ThirdPageProps = {
  user: userInfo;
  setUser: React.Dispatch<React.SetStateAction<userInfo>>;
  currentPage: number;
  setCurrentPage: React.Dispatch<React.SetStateAction<number>>;
};

export default function ThirdPage({
  user,
  setUser,
  currentPage,
  setCurrentPage,
}: ThirdPageProps) {
  const router = useRouter();

  const [insertEmail, setInsertEmail] = useState(user.email);
  const [checkAuthNumber, setCheckAuthNumber] = useState("");
  const [insertNickname, setInsertNickname] = useState(user.nickname);
  const [insertPassword, setInsertPassword] = useState("");
  const [checkPassword, setCheckPassword] = useState("");
  const [challenge, setChallenge] = useState<SignupChallenge>();
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const request = useRef(0);
  const requestedEmail = useRef<string>();
  const alive = useRef(true);
  const joinAttempted = useRef(false);
  useEffect(() => { alive.current = true; return () => { alive.current = false; request.current++; }; }, []);
  const isEmailInputDisabled = busy;
  const isCheckAuthNumberDisabled = busy || !challenge || !!user.emailProof;
  const isCheckAuthNumberHidden = !challenge;
  const isJoinDisabled = busy || !user.phoneProof || !user.emailProof || !insertNickname || !insertPassword || insertPassword !== checkPassword;
  const reset = () => {
    request.current++; requestedEmail.current = undefined; setChallenge(undefined); setCheckAuthNumber("");
    setUser(previous => ({ ...previous, attempt: undefined, phoneProof: undefined, emailProof: undefined }));
  };
  const leave = (page: number) => { if (pending.current) return; reset(); setCurrentPage(page); };
  const sendEmail = async () => {
    if (pending.current || !insertEmail) return;
    if (!user.attempt || !user.phoneProof) { alert("전화번호부터 다시 인증하세요."); leave(2); return; }
    pending.current = true; setBusy(true); const sequence = ++request.current;
    const previousTarget = requestedEmail.current;
    // The server can reserve this target before its response reaches the page.
    requestedEmail.current = insertEmail;
    try {
      const next = await signupPost<SignupChallenge>("/auth/email", { name: user.name, email: insertEmail, attemptId: user.attempt.attemptId, attemptSecret: user.attempt.attemptSecret });
      if (!alive.current || sequence !== request.current) return;
      if (!next.challengeId) throw new Error("인증을 다시 시작하세요.");
      setChallenge(next); setCheckAuthNumber("");
      setUser(previous => ({ ...previous, email: insertEmail, emailProof: undefined }));
      alert("입력하신 이메일로 인증번호를 전송했습니다.");
    } catch (error) {
      if (alive.current && sequence === request.current) {
        if (signupQuotaRejected(error)) {
          requestedEmail.current = previousTarget;
        } else {
          setChallenge(undefined); setCheckAuthNumber("");
          setUser(previous => ({ ...previous, emailProof: undefined }));
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
      const result = await signupPost<SignupProof>("/auth/email/verify", { attemptId: user.attempt.attemptId, attemptSecret: user.attempt.attemptSecret, challengeId: challenge.challengeId, code: checkAuthNumber });
      if (!alive.current || sequence !== request.current) return;
      if (!result.proof) throw new Error("인증을 다시 시작하세요.");
      setUser(previous => ({ ...previous, email: insertEmail, emailProof: result.proof }));
      setCheckAuthNumber(""); alert("인증되었습니다.");
    } catch (error) { if (alive.current && sequence === request.current) alert(signupMessage(error)); }
    finally { pending.current = false; if (alive.current) setBusy(false); }
  };
  const join = async () => {
    if (pending.current || joinAttempted.current || isJoinDisabled || !user.attempt) return;
    pending.current = true; joinAttempted.current = true; setBusy(true);
    try {
      const response = await fetch("/api/join", { method: "POST", headers: { "Content-type": "application/json" }, cache: "no-store",
        body: JSON.stringify({ name: user.name, email: insertEmail, phoneNumber: user.phoneNumber, nickname: insertNickname, password: insertPassword, attemptId: user.attempt.attemptId, attemptSecret: user.attempt.attemptSecret, emailProof: user.emailProof, phoneProof: user.phoneProof }) });
      if (!alive.current) return;
      if (response.status === 201) { reset(); router.push("/login"); return; }
      throw new Error("가입 결과를 확인할 수 없거나 가입에 실패했습니다. 이미 가입되었을 수 있으니 로그인하거나 두 인증을 다시 진행하세요.");
    } catch {
      if (alive.current) { reset(); alert("가입 결과를 확인할 수 없거나 가입에 실패했습니다. 이미 가입되었을 수 있으니 로그인하거나 두 인증을 다시 진행하세요."); setCurrentPage(2); }
    } finally { pending.current = false; if (alive.current) setBusy(false); }
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
            <button type="button" onClick={() => leave(2)}>
              본인확인
            </button>
            <span> {">"} </span>
            <span className="font-bold">정보입력</span>
            <span> {">"} </span>
            <span>가입완료</span>
          </div>
          <div className="flex-col h-auto mt-2">
            <div className="flex justify-between items-center w-full h-14">
              <div className="flex justify-center items-center w-[75%] p-2 rounded-md border-[1px] dark:bg-[#3B3B3B] border-black/45 h-14">
                <EmailImage className="w-8 mr-4 ml-2 fill-black/50 dark:fill-[#9CA3AF]" />
                <input
                  type="email"
                  placeholder="이메일"
                  value={insertEmail}
                  onChange={(e) => {
                    const email = e.target.value;
                    if (email !== insertEmail && (requestedEmail.current || challenge || user.emailProof)) reset();
                    setInsertEmail(email);
                  }}
                  disabled={isEmailInputDisabled}
                  className="w-full focus:outline-none text-xl"
                />
              </div>
              <button
                className="cursor-pointer flex justify-center items-center rounded-md text-white bg-point-color w-[20%] h-14 text-xl"
                disabled={busy}
                onClick={sendEmail}
              >
                <span>인증</span>
              </button>
            </div>
            <div
              className={`flex justify-between items-center w-full h-14 mt-2 ${isCheckAuthNumberHidden ? "hidden" : ""
                }`}
            >
              <div className="flex justify-center items-center w-[75%] p-2 rounded-md border-[1px] dark:bg-[#3B3B3B] border-black/45 h-14">
                <AuthCheck className="w-9 mr-4 ml-2 fill-black/50 dark:fill-[#9CA3AF]" />
                <input
                  type="text"
                  inputMode="numeric"
                  maxLength={6}
                  placeholder="인증번호"
                  value={checkAuthNumber}
                  onChange={(e) => setCheckAuthNumber(e.target.value)}
                  disabled={isCheckAuthNumberDisabled}
                  className="w-full focus:outline-none text-xl"
                />
              </div>
              <button
                className="cursor-pointer flex justify-center items-center rounded-md text-white bg-point-color w-[20%] h-14 text-xl"
                onClick={authCheck}
                disabled={isCheckAuthNumberDisabled}
              >
                <span>확인</span>
              </button>
            </div>
            <div className="flex justify-center items-center w-full p-2 rounded-md border-[1px] dark:bg-[#3B3B3B] border-black/45 h-14 mt-8">
              <PasswordImage className="w-9 mr-4 ml-2 fill-black/50 dark:fill-[#9CA3AF]" />
              <input
                type="password"
                placeholder="비밀번호"
                value={insertPassword}
                onChange={(e) => setInsertPassword(e.target.value)}
                className="w-full focus:outline-none text-xl"
              />
            </div>
            <div className="flex justify-center items-center w-full p-2 rounded-md border-[1px] dark:bg-[#3B3B3B] border-black/45 h-14 mt-2">
              <AuthCheck className="w-9 mr-4 ml-2 fill-black/50 dark:fill-[#9CA3AF]" />
              <input
                type="password"
                placeholder="비밀번호 확인"
                value={checkPassword}
                onChange={(e) => setCheckPassword(e.target.value)}
                className="w-full focus:outline-none text-xl"
              />
            </div>
            <div className="flex justify-center items-center w-full p-2 rounded-md border-[1px] dark:bg-[#3B3B3B] border-black/45 h-14 mt-8">
              <UserImage className="w-9 mr-4 ml-2 fill-black/50 dark:fill-[#9CA3AF]" />
              <input
                type="text"
                placeholder="닉네임"
                value={insertNickname}
                onChange={(e) => setInsertNickname(e.target.value)}
                className="w-full focus:outline-none text-xl"
              />
            </div>
          </div>
            <button type="button" disabled={busy} onClick={() => leave(2)} className="mt-4 underline">인증 처음부터 다시 시작</button>
            <div className="flex justify-between mt-12">
            <button
              className="cursor-pointer border-solid rounded-md dark:text-white dark:bg-[#3B3B3B] dark:border-black/45 text-black bg-gray-100 border-[2px] w-[calc(50%-1vw)] h-16 text-3xl"
              onClick={() => leave(currentPage - 1)}
            >
              <span>이전</span>
            </button>
            <button
              className="cursor-pointer border-solid rounded-md text-white bg-point-color w-[calc(50%-1vw)] h-16 text-3xl"
              onClick={join}
              disabled={isJoinDisabled}
            >
              가입
            </button>
          </div>
          <div className="flex justify-center">
            <Caution />
          </div>
        </div>
      </div>
    </div>
  );
}
