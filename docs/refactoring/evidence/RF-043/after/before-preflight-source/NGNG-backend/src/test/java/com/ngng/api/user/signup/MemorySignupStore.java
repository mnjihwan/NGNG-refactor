package com.ngng.api.user.signup;

import com.ngng.api.user.user.service.signup.SignupStore;
import java.util.*;

/** Behavioral double only. Its synchronization does NOT validate production Redis/Lua atomicity. */
final class MemorySignupStore implements SignupStore {
    long now = 1_000_000;
    int writes;
    int collisions;
    boolean unavailable;
    boolean consumeTimeout;
    final Map<String, Map<String,String>> attempts = new HashMap<>();
    final Map<String, LinkedHashMap<String,Long>> quotas = new HashMap<>();
    final Set<String> challengeKeys = new HashSet<>();
    List<String> result(String value) { return List.of(value); }
    void clear() { attempts.clear(); quotas.clear(); challengeKeys.clear(); now=1_000_000; writes=0; collisions=0; unavailable=false; consumeTimeout=false; }
    Map<String,String> attempt() { return attempts.values().iterator().next(); }
    String snapshot() { return new TreeMap<>(attempt()).toString() + quotas.toString() + challengeKeys.toString() + writes; }
    @Override public synchronized List<String> run(String operation, List<String> keys, String... a) {
        if (unavailable) return result("UNAVAILABLE");
        if (operation.equals("create")) {
            if (collisions-- > 0 || attempts.containsKey(keys.get(0))) return result("COLLISION");
            Map<String,String> state = new HashMap<>(); state.put("secret",a[0]); state.put("expiry",String.valueOf(now+1_800_000)); state.put("state","open");
            attempts.put(keys.get(0),state); writes++; return List.of("OK",state.get("expiry"));
        }
        Map<String,String> s = attempts.get(keys.get(0));
        if (s==null || !Objects.equals(s.get("secret"),a[0]) || now>=Long.parseLong(s.get("expiry"))) return result("REQUIRED");
        if (s.get("state").equals("consumed")) return result("RESTART");
        if (operation.equals("consume")) {
            for(int i=0;i<2;i++) {
                String c=i==0?"email":"phone"; int offset=1+i*2;
                if (!"verified".equals(s.get(c+":state")) || !Objects.equals(s.get(c+":target"),a[offset])
                    || !Objects.equals(s.get(c+":proof"),a[offset+1]) || now>=Long.parseLong(s.getOrDefault(c+":proofExpiry","0"))) return result("REQUIRED");
            }
            s.put("state","consumed"); s.put("email:state","consumed"); s.put("phone:state","consumed");
            s.remove("email:proof");s.remove("phone:proof");writes++;
            if (consumeTimeout) throw com.ngng.api.user.user.service.signup.SignupFailure.unavailable();
            return result("OK");
        }
        String c=a[1];
        if (operation.equals("issue")) {
            if (s.containsKey(c+":target") && !Objects.equals(s.get(c+":target"),a[2])) return result("REQUIRED");
            if (Integer.parseInt(s.getOrDefault(c+":checks","0"))>=5) return List.of("LIMIT","0");
            var q=quotas.getOrDefault(keys.get(1),new LinkedHashMap<>());
            if (collisions-- > 0 || challengeKeys.contains(keys.get(2)) || q.containsKey(a[5])) return result("COLLISION");
            List<Long> recent=q.values().stream().filter(t->t>now-600_000).sorted().toList();
            if (recent.size()>=3) return List.of("LIMIT",String.valueOf(Math.max(1,(recent.get(0)+600_000-now+999)/1000)));
            q.values().removeIf(t->t<=now-600_000);q.put(a[5],now);quotas.put(keys.get(1),q);challengeKeys.add(keys.get(2));
            s.put(c+":target",a[2]);s.put(c+":generation",a[3]);s.put(c+":code",a[4]);s.put(c+":codeExpiry",String.valueOf(Math.min(now+300_000,Long.parseLong(s.get("expiry")))));
            s.put(c+":state","pending");s.remove(c+":proof");s.remove(c+":proofExpiry");writes++;return List.of("OK",s.get(c+":codeExpiry"));
        }
        if (operation.equals("complete")) {
            if (!Objects.equals(s.get(c+":generation"),a[2]) || !"pending".equals(s.get(c+":state"))) return result("RESTART");
            if (a[3].equals("sent") && now>=Long.parseLong(s.get(c+":codeExpiry"))) return result("REQUIRED");
            s.put(c+":state",a[3].equals("sent")?"sent":"failed");if(!a[3].equals("sent"))s.remove(c+":code");writes++;return result("OK");
        }
        if (operation.equals("verify")) {
            if ("verified".equals(s.get(c+":state"))) return result("RESTART");
            if (!Objects.equals(s.get(c+":generation"),a[2]) || !"sent".equals(s.get(c+":state")) || now>=Long.parseLong(s.get(c+":codeExpiry"))) return result("REQUIRED");
            int checks=Integer.parseInt(s.getOrDefault(c+":checks","0"));if(checks>=5)return List.of("LIMIT","0");
            s.put(c+":checks",String.valueOf(checks+1));writes++;
            if(!Objects.equals(s.get(c+":code"),a[3]))return checks==4?List.of("LIMIT","0"):result("REQUIRED");
            s.put(c+":state","verified");s.put(c+":proof",a[4]);s.put(c+":proofExpiry",String.valueOf(Math.min(now+300_000,Long.parseLong(s.get("expiry")))));s.remove(c+":code");
            return List.of("OK",s.get(c+":proofExpiry"));
        }
        throw new AssertionError("unexpected operation");
    }
}
