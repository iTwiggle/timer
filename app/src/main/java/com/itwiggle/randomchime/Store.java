package com.itwiggle.randomchime;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class Store {
    static final String PREFS = "random_chime";
    static android.content.SharedPreferences p(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static void addRecordings(Context c, List<Uri> uris) {
        try {
            JSONArray a = new JSONArray(p(c).getString("recordings", "[]"));
            for (Uri u : uris) { JSONObject o = new JSONObject(); o.put("uri", u.toString()); o.put("name", u.getLastPathSegment()); o.put("enabled", true); a.put(o); }
            p(c).edit().putString("recordings", a.toString()).apply();
        } catch (Exception ignored) {}
    }

    static JSONArray recordings(Context c) { try { return new JSONArray(p(c).getString("recordings", "[]")); } catch (Exception e) { return new JSONArray(); } }

    static Uri nextRecording(Context c) {
        try {
            JSONArray a = recordings(c); List<Integer> enabled = new ArrayList<>();
            for (int i=0;i<a.length();i++) if (a.getJSONObject(i).optBoolean("enabled", true)) enabled.add(i);
            if (enabled.isEmpty()) return null;
            String cycleRaw = p(c).getString("shuffle", "[]"); JSONArray cycle = new JSONArray(cycleRaw);
            List<Integer> queue = new ArrayList<>(); for(int i=0;i<cycle.length();i++) if(enabled.contains(cycle.getInt(i))) queue.add(cycle.getInt(i));
            if(queue.isEmpty()) { queue.addAll(enabled); Collections.shuffle(queue); }
            int pick = queue.remove(0); JSONArray next = new JSONArray(); for(int i:queue) next.put(i);
            p(c).edit().putString("shuffle", next.toString()).apply();
            return Uri.parse(a.getJSONObject(pick).getString("uri"));
        } catch(Exception e) { return null; }
    }

    static void addHistory(Context c, String action) {
        try { JSONArray a = new JSONArray(p(c).getString("history", "[]")); JSONObject o = new JSONObject(); o.put("action", action); o.put("at", System.currentTimeMillis()); a.put(o); while(a.length()>200) a.remove(0); p(c).edit().putString("history", a.toString()).apply(); } catch(Exception ignored) {}
    }

    static int dailyCount(Context c) {
        boolean random = p(c).getBoolean("random", false); int exact=p(c).getInt("exact",3), min=p(c).getInt("min",2), max=p(c).getInt("max",5);
        return random ? min + new java.util.Random().nextInt(Math.max(1, max-min+1)) : exact;
    }
}
