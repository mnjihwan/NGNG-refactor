export type SignupAttempt = { attemptId: string; attemptSecret: string; expiresAt: number };
export type SignupChallenge = { challengeId: string; expiresAt: number };
export type SignupProof = { proof: string; expiresAt: number };

class SignupRequestError extends Error {
  constructor(message: string, readonly status?: number) { super(message); }
}
export const signupQuotaRejected = (error: unknown) => error instanceof SignupRequestError && error.status === 429;

export async function signupPost<T>(path: string, body: object, expected = 200): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api/join${path}`, {
      method: "POST", headers: { "Content-type": "application/json" },
      cache: "no-store", body: JSON.stringify(body),
    });
  } catch {
    throw new SignupRequestError("인증 요청 상태를 확인할 수 없습니다. 다시 인증하세요.");
  }
  if (response.status !== expected) {
    if (response.status === 429) throw new SignupRequestError("인증 한도에 도달했습니다. 잠시 후 또는 새 인증으로 다시 시도하세요.", 429);
    if (response.status === 409) throw new SignupRequestError("중복 또는 현재 발급 불가 상태입니다. 인증을 다시 시작하세요.", 409);
    throw new SignupRequestError("인증 요청에 실패했습니다. 만료되었거나 상태를 확인할 수 없습니다. 다시 인증하세요.", response.status);
  }
  let result;
  try { result = await response.json(); }
  catch { throw new SignupRequestError("인증 응답을 확인할 수 없습니다. 다시 인증하세요."); }
  const token = (value: unknown) => typeof value === "string" && /^[A-Za-z0-9_-]{43}$/.test(value);
  const object = result && typeof result === "object" && !Array.isArray(result);
  const valid = object && Number.isSafeInteger(result.expiresAt) && result.expiresAt > 0 && (
    path === "/auth/attempt" ? token(result.attemptId) && token(result.attemptSecret) :
    path === "/auth/email" || path === "/auth/phonenumber" ? token(result.challengeId) :
    path === "/auth/email/verify" || path === "/auth/phonenumber/verify" ? token(result.proof) : false
  );
  if (!valid) throw new SignupRequestError("인증 응답을 확인할 수 없습니다. 다시 인증하세요.");
  return result as T;
}

export const signupMessage = (error: unknown) => error instanceof Error ? error.message : "요청 상태를 확인할 수 없습니다. 다시 인증하세요.";
