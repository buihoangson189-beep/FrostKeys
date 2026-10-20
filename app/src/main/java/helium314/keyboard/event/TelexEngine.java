package helium314.keyboard.event;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Telex engine thuần logic, không phụ thuộc Android.
 * Dùng: composing = TelexEngine.type(composing, keyChar);
 * "composing" là từ đang gõ dở (chưa nhấn space).
 */
public final class TelexEngine {
    private static final int NONE = 0, CIRC = 1, BREVE = 2, HORN = 3, STROKE = 4;
    private static final String VOWELS = "aeiouy";
    // true: hòa, thủy (kiểu Unikey mặc định) | false: hoà, thuỷ
    public static boolean OLD_STYLE_TONE = true;

    private static final class Letter {
        char base;      // chữ thường
        boolean upper;
        int shape = NONE;
        Letter(char b, boolean u) { base = b; upper = u; }
    }

    private static final class Word {
        List<Letter> letters = new ArrayList<>();
        char tone = 0; // 0, 's','f','r','x','j'
    }

    private static char toneKey(char mark) {
        switch (mark) {
            case '\u0301': return 's';
            case '\u0300': return 'f';
            case '\u0309': return 'r';
            case '\u0303': return 'x';
            case '\u0323': return 'j';
        }
        return 0;
    }

    private static char toneMark(char key) {
        switch (key) {
            case 's': return '\u0301';
            case 'f': return '\u0300';
            case 'r': return '\u0309';
            case 'x': return '\u0303';
            case 'j': return '\u0323';
        }
        return 0;
    }

    private static Word parse(String s) {
        Word w = new Word();
        String nfd = Normalizer.normalize(s, Normalizer.Form.NFD);
        for (int i = 0; i < nfd.length(); i++) {
            char c = nfd.charAt(i);
            if (c == '\u0302' || c == '\u0306' || c == '\u031B') {
                if (!w.letters.isEmpty()) {
                    w.letters.get(w.letters.size() - 1).shape =
                        c == '\u0302' ? CIRC : c == '\u0306' ? BREVE : HORN;
                }
            } else if (toneKey(c) != 0) {
                w.tone = toneKey(c);
            } else if (c == '\u0111' || c == '\u0110') {
                Letter l = new Letter('d', c == '\u0110');
                l.shape = STROKE;
                w.letters.add(l);
            } else {
                w.letters.add(new Letter(Character.toLowerCase(c), Character.isUpperCase(c)));
            }
        }
        return w;
    }

    private static boolean isVowel(Word w, int i) {
        char c = w.letters.get(i).base;
        if (VOWELS.indexOf(c) < 0) return false;
        // u trong "qu" là phụ âm
        if (c == 'u' && i > 0 && w.letters.get(i - 1).base == 'q') return false;
        // i trong "gi" + nguyên âm là phụ âm
        if (c == 'i' && i == 1 && w.letters.get(0).base == 'g'
                && w.letters.size() > 2 && VOWELS.indexOf(w.letters.get(2).base) >= 0) return false;
        return true;
    }

    private static int tonePosition(Word w) {
        List<Integer> v = new ArrayList<>();
        for (int i = 0; i < w.letters.size(); i++) if (isVowel(w, i)) v.add(i);
        if (v.isEmpty()) return -1;
        if (v.size() == 1) return v.get(0);
        // ưu tiên nguyên âm có dấu mũ/móc/trăng, lấy cái cuối (ươ -> ơ)
        int marked = -1;
        for (int i : v) {
            int sh = w.letters.get(i).shape;
            if (sh == CIRC || sh == BREVE || sh == HORN) marked = i;
        }
        if (marked >= 0) return marked;
        if (v.size() >= 3) return v.get(1); // oai, uyê...
        boolean hasFinal = v.get(v.size() - 1) < w.letters.size() - 1;
        if (hasFinal) return v.get(1);       // toán, loạn
        return OLD_STYLE_TONE ? v.get(0) : v.get(1) == v.get(0) + 1 && isNewStylePair(w, v) ? v.get(1) : v.get(0);
    }

    // kiểu mới: oa, oe, uy đặt dấu ở chữ thứ hai
    private static boolean isNewStylePair(Word w, List<Integer> v) {
        String p = "" + w.letters.get(v.get(0)).base + w.letters.get(v.get(1)).base;
        return p.equals("oa") || p.equals("oe") || p.equals("uy");
    }

    private static String render(Word w) {
        int tp = w.tone != 0 ? tonePosition(w) : -1;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w.letters.size(); i++) {
            Letter l = w.letters.get(i);
            if (l.shape == STROKE) {
                sb.append(l.upper ? '\u0110' : '\u0111');
                continue;
            }
            StringBuilder one = new StringBuilder();
            one.append(l.upper ? Character.toUpperCase(l.base) : l.base);
            if (l.shape == CIRC) one.append('\u0302');
            if (l.shape == BREVE) one.append('\u0306');
            if (l.shape == HORN) one.append('\u031B');
            if (i == tp) one.append(toneMark(w.tone));
            sb.append(Normalizer.normalize(one.toString(), Normalizer.Form.NFC));
        }
        return sb.toString();
    }

    private static String literal(String composing, char key) {
        return composing + key;
    }

    /** Xử lý một phím, trả về chuỗi đang soạn mới. */
    public static String type(String composing, char key) {
        char k = Character.toLowerCase(key);
        boolean up = Character.isUpperCase(key);
        Word w = parse(composing);
        int n = w.letters.size();
        boolean hasVowel = false;
        for (int i = 0; i < n; i++) if (isVowel(w, i)) hasVowel = true;

        // ---- dấu thanh ----
        if (k == 's' || k == 'f' || k == 'r' || k == 'x' || k == 'j') {
            if (!hasVowel) return literal(composing, key);
            if (w.tone == k) {            // gõ lặp -> hủy dấu, ra chữ thường
                w.tone = 0;
                return render(w) + key;
            }
            w.tone = k;
            return render(w);
        }
        if (k == 'z') {
            if (w.tone != 0) { w.tone = 0; return render(w); }
            return literal(composing, key);
        }

        // ---- dd -> đ ----
        if (k == 'd' && n > 0) {
            Letter last = w.letters.get(n - 1);
            if (last.base == 'd' && last.shape == NONE) { last.shape = STROKE; return render(w); }
            if (last.base == 'd' && last.shape == STROKE) { last.shape = NONE; return render(w) + key; }
        }

        // ---- aa, ee, oo -> â, ê, ô ----
        if ((k == 'a' || k == 'e' || k == 'o') && n > 0) {
            Letter last = w.letters.get(n - 1);
            if (last.base == k && last.shape == NONE) { last.shape = CIRC; return render(w); }
            if (last.base == k && last.shape == CIRC) { last.shape = NONE; return render(w) + key; }
        }

        // ---- w: ă, ơ, ư, ươ ----
        if (k == 'w') {
            if (composing.equals("\u01B0") || composing.equals("\u01AF")) return composing.equals("\u01B0") ? "w" : "W";
            // cặp "uo" -> "ươ" (trừ "quo" -> "quơ")
            for (int i = 0; i + 1 < n; i++) {
                Letter a = w.letters.get(i), b = w.letters.get(i + 1);
                boolean afterQ = i > 0 && w.letters.get(i - 1).base == 'q';
                if (a.base == 'u' && b.base == 'o' && !afterQ
                        && (a.shape == NONE || a.shape == HORN) && (b.shape == NONE || b.shape == HORN)) {
                    if (a.shape == HORN && b.shape == HORN) {
                        a.shape = NONE; b.shape = NONE; return render(w) + key;
                    }
                    a.shape = HORN; b.shape = HORN; return render(w);
                }
            }
            for (int i = n - 1; i >= 0; i--) {
                Letter l = w.letters.get(i);
                if (l.base == 'a' || l.base == 'o' || l.base == 'u') {
                    int want = l.base == 'a' ? BREVE : HORN;
                    if (l.shape == want) { l.shape = NONE; return render(w) + key; }
                    if (l.shape == NONE || l.shape == CIRC) { l.shape = want; return render(w); }
                    break;
                }
            }
            // không có a/o/u để biến đổi: w đứng sau phụ âm -> ư
            boolean endsWithVowel = n > 0 && VOWELS.indexOf(w.letters.get(n - 1).base) >= 0;
            if (!endsWithVowel) return composing + (up ? '\u01AF' : '\u01B0');
            return literal(composing, key);
        }

        return literal(composing, key);
    }
}
