package wikirmi.client.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Window;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

/** Swing helpers: run remote calls off the EDT and turn exceptions into friendly dialogs. */
public final class UiUtils {
    private UiUtils() {}

    // shared palette
    public static final Color ACCENT     = new Color(0x2D6CDF);   // primary blue
    public static final Color ACCENT_DARK = new Color(0x1F4DA0);
    public static final Color BANNER_BG  = new Color(0x2D6CDF);
    public static final Color BANNER_FG  = Color.WHITE;
    public static final Color STATUS_BG  = new Color(0xE7EDF9);
    public static final Color SUCCESS    = new Color(0x2E9E4F);   // "add" actions

    /** Centre a window on screen with a small per-process offset so multiple clients don't stack. */
    public static void placeWindow(Window w) {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        long pid = ProcessHandle.current().pid();
        int dx = (int) ((pid % 6) * 46) - 115;
        int dy = (int) ((pid / 6 % 6) * 34) - 85;
        int x = Math.max(0, (screen.width - w.getWidth()) / 2 + dx);
        int y = Math.max(0, (screen.height - w.getHeight()) / 2 + dy);
        w.setLocation(x, y);
    }

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /** Runs {@code task} on a background thread; on success calls {@code onSuccess} on the EDT,
     *  on failure shows an error dialog. Keeps the UI responsive during RMI calls. */
    public static <T> void async(Component owner, Callable<T> task, Consumer<T> onSuccess) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return task.call(); }
            @Override protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (Exception ex) {
                    error(owner, ex);
                }
            }
        }.execute();
    }

    public static void error(Component owner, Throwable t) {
        Throwable r = t;
        while (r instanceof ExecutionException && r.getCause() != null) r = r.getCause();
        String msg = (r.getMessage() != null) ? r.getMessage() : r.toString();
        JOptionPane.showMessageDialog(owner, msg, "Błąd", JOptionPane.ERROR_MESSAGE);
    }

    public static void error(Component owner, String msg) {
        JOptionPane.showMessageDialog(owner, msg, "Błąd", JOptionPane.ERROR_MESSAGE);
    }

    public static void info(Component owner, String msg) {
        JOptionPane.showMessageDialog(owner, msg, "Informacja", JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component owner, String msg) {
        return JOptionPane.showConfirmDialog(owner, msg, "Potwierdzenie", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    public static String formatTime(long epochMillis) {
        if (epochMillis <= 0) return "-";
        return TIME_FMT.format(Instant.ofEpochMilli(epochMillis));
    }
}
