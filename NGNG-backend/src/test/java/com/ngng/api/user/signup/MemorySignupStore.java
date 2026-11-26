package com.ngng.api.user.signup;

import com.ngng.api.user.user.service.signup.SignupStore;
import java.util.*;

/** Behavioral double only. Its synchronization does NOT validate production Redis/Lua atomicity. */
final class MemorySignupStore implements SignupStore {
    private static final long VERIFICATION_TTL_MILLIS = 300_000;

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
    boolean digest(String v) { return v != null && v.matches("[0-9a-f]{64}"); }
    boolean token(String v) { return v != null && v.matches("[A-Za-z0-9_-]{43}"); }
    String[] keyParts(String key) {
        if(key==null)return null;String[] p=key.split(":",-1);
        return p.length==7&&Set.of("local","development","test","staging","production").contains(p[0])&&p[1].equals("{signup}")&&p[2].matches("[A-Za-z0-9_-]{16,64}")&&p[3].equals("signup")&&digest(p[6])?p:null;
    }
    boolean validKeys(List<String>keys,String channel) {
        String[] attempt=keyParts(keys.get(0));if(attempt==null||!attempt[4].equals("both")||!attempt[5].equals("attempt"))return false;
        if(keys.size()==1)return true;
        for(int i=1;i<3;i++){String[] p=keyParts(keys.get(i));if(p==null||!p[0].equals(attempt[0])||!p[2].equals(attempt[2])||!p[4].equals(channel)||!p[5].equals(i==1?"quota":"challenge"))return false;}
        return true;
    }
    boolean validRequest(String operation,List<String> keys,String[] a) {
        int count=switch(operation){case "create"->1;case "issue"->6;case "complete"->4;case "verify","consume"->5;default->0;};
        if(count==0||a.length!=count||keys.size()!=(operation.equals("issue")?3:1)||!digest(a[0])||!validKeys(keys,operation.equals("issue")?a[1]:null))return false;
        if(operation.equals("create"))return true;
        if(operation.equals("consume"))return Arrays.stream(a).allMatch(this::digest);
        if(!"email".equals(a[1])&&!"phone".equals(a[1]))return false;
        return switch(operation){case "issue"->new HashSet<>(keys).size()==3&&digest(a[2])&&token(a[3])&&digest(a[4])&&token(a[5]);case "complete"->token(a[2])&&("sent".equals(a[3])||"failed".equals(a[3]));case "verify"->token(a[2])&&digest(a[3])&&digest(a[4]);default->false;};
    }
    boolean validChannel(Map<String,String>s,String c,long expiry) {
        String state=s.get(c+":state");
        if(state==null)return List.of("target","generation","checks","code","codeExpiry","proof","proofExpiry").stream().noneMatch(f->s.containsKey(c+":"+f));
        try {
            long latestExpiry = Math.min(now + VERIFICATION_TTL_MILLIS, expiry);
            int count=Integer.parseInt(s.get(c+":checks"));long codeExpiry=Long.parseLong(s.get(c+":codeExpiry"));
            if(!digest(s.get(c+":target"))||!token(s.get(c+":generation"))||count<0||count>5||codeExpiry<1||codeExpiry>latestExpiry)return false;
            return switch(state){
                case "pending","sent"->digest(s.get(c+":code"))&&!s.containsKey(c+":proof")&&!s.containsKey(c+":proofExpiry");
                case "failed"->!s.containsKey(c+":code")&&!s.containsKey(c+":proof")&&!s.containsKey(c+":proofExpiry");
                case "verified" -> {
                    long proofExpiry = Long.parseLong(s.get(c+":proofExpiry"));
                    yield count >= 1 && !s.containsKey(c+":code") && digest(s.get(c+":proof"))
                            && proofExpiry >= 1 && proofExpiry <= latestExpiry;
                }
                default->false;
            };
        }catch(RuntimeException invalid){return false;}
    }
    @Override public synchronized List<String> run(String operation, List<String> keys, String... a) {
        if (unavailable) return result("UNAVAILABLE");
        if(!validRequest(operation,keys,a))return result("UNAVAILABLE");
        if (operation.equals("create")) {
            if (collisions-- > 0 || attempts.containsKey(keys.get(0))) return result("COLLISION");
            Map<String,String> state = new HashMap<>(); state.put("secret",a[0]); state.put("expiry",String.valueOf(now+1_800_000)); state.put("state","open");
            attempts.put(keys.get(0),state); writes++; return List.of("OK",state.get("expiry"));
        }
        Map<String,String> s = attempts.get(keys.get(0));
        if (s==null || !Objects.equals(s.get("secret"),a[0])) return result("REQUIRED");
        long expiry;try{expiry=Long.parseLong(s.get("expiry"));}catch(RuntimeException invalid){return result("UNAVAILABLE");}
        if(expiry<1||expiry>9_007_199_254_740_991L||expiry>now+1_800_000)return result("UNAVAILABLE");
        if(now>=expiry)return result("REQUIRED");
        if ("consumed".equals(s.get("state"))) return result("RESTART");
        if(!"open".equals(s.get("state")))return result("UNAVAILABLE");
        if (operation.equals("consume")) {
            for(int i=0;i<2;i++) {
                String c=i==0?"email":"phone"; int offset=1+i*2;
                if(!validChannel(s,c,expiry))return result("UNAVAILABLE");
                if (!"verified".equals(s.get(c+":state")) || !Objects.equals(s.get(c+":target"),a[offset])
                    || !Objects.equals(s.get(c+":proof"),a[offset+1]) || now>=Long.parseLong(s.getOrDefault(c+":proofExpiry","0"))) return result("REQUIRED");
            }
            s.put("state","consumed"); s.put("email:state","consumed"); s.put("phone:state","consumed");
            s.remove("email:proof");s.remove("phone:proof");writes++;
            if (consumeTimeout) throw com.ngng.api.user.user.service.signup.SignupFailure.unavailable();
            return result("OK");
        }
        String c=a[1];
        if(!validChannel(s,c,expiry))return result("UNAVAILABLE");
        if (operation.equals("issue")) {
            if (s.containsKey(c+":target") && !Objects.equals(s.get(c+":target"),a[2])) return result("REQUIRED");
            if (Integer.parseInt(s.getOrDefault(c+":checks","0"))>=5) return List.of("LIMIT","0");
            var q=quotas.getOrDefault(keys.get(1),new LinkedHashMap<>());
            if(q.size()>3||q.entrySet().stream().anyMatch(e->!token(e.getKey())||e.getValue()<1||e.getValue()>now))return result("UNAVAILABLE");
            if (collisions-- > 0 || challengeKeys.contains(keys.get(2)) || q.containsKey(a[5])) return result("COLLISION");
            List<Long> recent=q.values().stream().filter(t->t>now-600_000).sorted().toList();
            if (recent.size()>=3) return List.of("LIMIT",String.valueOf(Math.max(1,(recent.get(0)+600_000-now+999)/1000)));
            q.values().removeIf(t->t<=now-600_000);q.put(a[5],now);quotas.put(keys.get(1),q);challengeKeys.add(keys.get(2));
            s.put(c+":target",a[2]);s.put(c+":generation",a[3]);s.put(c+":code",a[4]);s.put(c+":codeExpiry",String.valueOf(Math.min(now + VERIFICATION_TTL_MILLIS, expiry)));
            s.putIfAbsent(c+":checks","0");s.put(c+":state","pending");s.remove(c+":proof");s.remove(c+":proofExpiry");writes++;return List.of("OK",s.get(c+":codeExpiry"));
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
            s.put(c+":state","verified");s.put(c+":proof",a[4]);s.put(c+":proofExpiry",String.valueOf(Math.min(now + VERIFICATION_TTL_MILLIS, expiry)));s.remove(c+":code");
            return List.of("OK",s.get(c+":proofExpiry"));
        }
        throw new AssertionError("unexpected operation");
    }
}
