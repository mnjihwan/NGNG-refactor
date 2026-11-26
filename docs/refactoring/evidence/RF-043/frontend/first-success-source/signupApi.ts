export type SignupAttempt = { attemptId: string; attemptSecret: string; expiresAt: number };
export type SignupChallenge = { challengeId: string; expiresAt: number };
export type SignupProof = { proof: string; expiresAt: number };

export async function signupPost<T>(path: string, body: object, expected = 200): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api/join${path}`, {
      method: "POST", headers: { "Content-type": "application/json" },
      cache: "no-store", body: JSON.stringify(body),
    });
  } catch {
    throw new Error("인증 요청 상태를 확인할 수 없습니다. 다시 인증하세요.");
  }
  if (response.status !== expected) {
    if (response.status === 429) throw new Error("인증 한도에 도달했습니다. 잠시 후 또는 새 인증으로 다시 시도하세요.");
    if (response.status === 409) throw new Error("중복 또는 현재 발급 불가 상태입니다. 인증을 다시 시작하세요.");
    throw new Error("인증 요청에 실패했습니다. 만료되었거나 상태를 확인할 수 없습니다. 다시 인증하세요.");
  }
  let result;
  try { result = await response.json(); }
  catch { throw new Error("인증 응답을 확인할 수 없습니다. 다시 인증하세요."); }
  if (!result || typeof result !== "object" || !Number.isFinite(result.expiresAt)) throw new Error("인증 응답을 확인할 수 없습니다. 다시 인증하세요.");
  return result as T;
}

export const signupMessage = (error: unknown) => error instanceof Error ? error.message : "요청 상태를 확인할 수 없습니다. 다시 인증하세요.";
