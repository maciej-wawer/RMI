package wikirmi.test;

import java.lang.reflect.Method;

/**
 * Runs every registered test (each exposes {@code public static void run() throws Exception}),
 * prints a PASS/FAIL line per test, and exits non-zero if any failed. This output is the
 * concurrency-correctness evidence captured in the project report.
 */
public class TestRunner {

    /** Pięć testów STRICTE pod współbieżność (każdy = jeden scenariusz, z polską narracją). */
    static final String[] TESTS = {
        "wikirmi.test.Demo1_UsuwanieEdytowanejStrony",        // usuwanie strony w trakcie edycji
        "wikirmi.test.Demo2_WymuszoneOdblokowanie",           // admin zdejmuje blokadę
        "wikirmi.test.Demo3_DwochNaRazNieWejdzie",            // wyścig: 2 osoby + 10 wątków, 1 wygrywa
        "wikirmi.test.Demo4_UsuniecieEdytujacegoUzytkownika", // usunięcie edytującego użytkownika
        "wikirmi.test.Demo5_BrakUtraconychZapisow",           // 50 wątków, brak lost update
    };

    public static void main(String[] args) throws Exception {
        System.out.println("=== WikiRMI test suite ===\n");
        int pass = 0, fail = 0, skip = 0;
        for (String className : TESTS) {
            Class<?> cls;
            try {
                cls = Class.forName(className);
            } catch (ClassNotFoundException notYetWritten) {
                System.out.printf("SKIP  %-40s (not yet implemented)%n", shortName(className));
                skip++;
                continue;
            }
            try {
                Method m = cls.getMethod("run");
                long t0 = System.currentTimeMillis();
                m.invoke(null);
                long ms = System.currentTimeMillis() - t0;
                System.out.printf("PASS  %-40s (%d ms)%n", shortName(className), ms);
                pass++;
            } catch (Throwable t) {
                Throwable cause = (t.getCause() != null) ? t.getCause() : t;
                System.out.printf("FAIL  %-40s -> %s%n", shortName(className), cause);
                fail++;
            }
        }
        System.out.println("\n=== " + pass + " passed, " + fail + " failed, " + skip + " skipped ===");
        if (fail > 0) System.exit(1);
    }

    private static String shortName(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot < 0 ? fqcn : fqcn.substring(dot + 1);
    }
}
