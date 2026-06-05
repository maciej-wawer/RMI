package wikirmi.server.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, dependency-free JSON reader/writer. Supports the value types we persist:
 * {@code Map<String,Object>}, {@code List<Object>}, {@code String}, {@code Long}/{@code Double},
 * {@code Boolean} and {@code null}. The writer pretty-prints (2-space indent) so the data file
 * stays human-readable.
 */
public final class Json {
    private Json() {}

    // ----------------------------------------------------------------- writer
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, 0);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object v, int indent) {
        if (v == null) sb.append("null");
        else if (v instanceof String) writeString(sb, (String) v);
        else if (v instanceof Boolean || v instanceof Number) sb.append(v.toString());
        else if (v instanceof Map) writeObject(sb, (Map<?, ?>) v, indent);
        else if (v instanceof List) writeArray(sb, (List<?>) v, indent);
        else writeString(sb, v.toString());
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> m, int indent) {
        if (m.isEmpty()) { sb.append("{}"); return; }
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<?, ?> e : m.entrySet()) {
            indent(sb, indent + 1);
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(": ");
            writeValue(sb, e.getValue(), indent + 1);
            if (++i < m.size()) sb.append(',');
            sb.append('\n');
        }
        indent(sb, indent);
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, List<?> list, int indent) {
        if (list.isEmpty()) { sb.append("[]"); return; }
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            indent(sb, indent + 1);
            writeValue(sb, list.get(i), indent + 1);
            if (i + 1 < list.size()) sb.append(',');
            sb.append('\n');
        }
        indent(sb, indent);
        sb.append(']');
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    private static void indent(StringBuilder sb, int n) {
        for (int i = 0; i < n; i++) sb.append("  ");
    }

    // ----------------------------------------------------------------- parser
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.parseValue();
        p.skipWs();
        if (!p.atEnd()) throw new IllegalArgumentException("Nieoczekiwane dane na pozycji " + p.pos);
        return v;
    }

    private static final class Parser {
        final String s;
        int pos;
        Parser(String s) { this.s = s; }

        boolean atEnd() { return pos >= s.length(); }
        char peek() { return s.charAt(pos); }
        char next() { return s.charAt(pos++); }

        void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
                else break;
            }
        }

        Object parseValue() {
            skipWs();
            char c = peek();
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default:  return parseNumber();
            }
        }

        Map<String, Object> parseObject() {
            Map<String, Object> m = new LinkedHashMap<>();
            next(); // consume '{'
            skipWs();
            if (peek() == '}') { next(); return m; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                if (next() != ':') throw new IllegalArgumentException("Oczekiwano ':' na pozycji " + (pos - 1));
                m.put(key, parseValue());
                skipWs();
                char c = next();
                if (c == '}') break;
                if (c != ',') throw new IllegalArgumentException("Oczekiwano ',' lub '}' na pozycji " + (pos - 1));
            }
            return m;
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            next(); // consume '['
            skipWs();
            if (peek() == ']') { next(); return list; }
            while (true) {
                list.add(parseValue());
                skipWs();
                char c = next();
                if (c == ']') break;
                if (c != ',') throw new IllegalArgumentException("Oczekiwano ',' lub ']' na pozycji " + (pos - 1));
            }
            return list;
        }

        String parseString() {
            skipWs();
            if (next() != '"') throw new IllegalArgumentException("Oczekiwano '\"' na pozycji " + (pos - 1));
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') break;
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'u':  sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16)); pos += 4; break;
                        default:   throw new IllegalArgumentException("Nieprawidłowa sekwencja \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Object parseNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') pos++;
                else break;
            }
            String num = s.substring(start, pos);
            if (num.contains(".") || num.contains("e") || num.contains("E")) return Double.parseDouble(num);
            return Long.parseLong(num);
        }

        void expect(String word) {
            if (!s.startsWith(word, pos)) throw new IllegalArgumentException("Oczekiwano '" + word + "' na pozycji " + pos);
            pos += word.length();
        }
    }
}
