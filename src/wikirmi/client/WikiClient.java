package wikirmi.client;

import java.awt.Color;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatLightLaf;

import wikirmi.client.gui.LoginFrame;

/** Punkt wejścia klienta: ustawia nowoczesny wygląd FlatLaf i pokazuje okno logowania. */
public class WikiClient {
    public static void main(String[] args) {
        applyLookAndFeel();
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }

    /** Nowoczesny, płaski wygląd (FlatLaf) z zaokrągleniami i akcentem. */
    public static void applyLookAndFeel() {
        try {
            FlatLightLaf.setup();
            UIManager.put("Button.arc", 14);
            UIManager.put("Component.arc", 12);
            UIManager.put("TextComponent.arc", 10);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.width", 12);
            UIManager.put("Component.focusWidth", 1);
            UIManager.put("Component.accentColor", new Color(0x2D6CDF));
            UIManager.put("TabbedPane.showTabSeparators", true);
            UIManager.put("Table.showHorizontalLines", true);
        } catch (Exception ignored) {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) { /* ostateczny fallback */ }
        }
    }
}
