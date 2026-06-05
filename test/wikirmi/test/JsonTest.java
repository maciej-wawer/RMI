package wikirmi.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import wikirmi.server.store.Json;

/** Round-trips every value type through the hand-rolled JSON codec, including string escapes. */
public class JsonTest {
    @SuppressWarnings("unchecked")
    public static void run() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", "powiedział \"cześć\"\ni wyszedł\tżegnaj");
        m.put("n", 42L);
        m.put("d", 3.5);
        m.put("b", true);
        m.put("nil", null);
        List<Object> arr = new ArrayList<>();
        arr.add("a");
        arr.add(7L);
        m.put("arr", arr);
        m.put("empty", new LinkedHashMap<String, Object>());

        String text = Json.write(m);
        Map<String, Object> r = (Map<String, Object>) Json.parse(text);

        Assert.eq(r.get("s"), "powiedział \"cześć\"\ni wyszedł\tżegnaj", "string with quotes/newline/tab round-trips");
        Assert.eq(((Number) r.get("n")).longValue(), 42, "integer parses as Long");
        Assert.isTrue(Math.abs(((Number) r.get("d")).doubleValue() - 3.5) < 1e-9, "decimal parses as Double");
        Assert.eq(r.get("b"), Boolean.TRUE, "boolean round-trips");
        Assert.isTrue(r.get("nil") == null, "null round-trips");
        Assert.eq(((List<?>) r.get("arr")).size(), 2, "array size round-trips");
        Assert.eq(((List<?>) r.get("arr")).get(1), 7L, "array element round-trips");
        Assert.isTrue(((Map<?, ?>) r.get("empty")).isEmpty(), "empty object round-trips");
    }
}
