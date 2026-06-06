package wikirmi.client;

import java.awt.Color;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import wikirmi.client.gui.LoginFrame;

/** Client entry point: applies the Nimbus look & feel, then shows the login window. */
public class WikiClient {
    public static void main(String[] args) {
        applyLookAndFeel();
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }

    private static void applyLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    // subtle cohesive blue theme + readable defaults
                    UIManager.put("nimbusBase", new Color(0x3C5A99));
                    UIManager.put("nimbusFocus", new Color(0x2D6CDF));
                    UIManager.put("control", new Color(0xF3F5FA));
                    UIManager.put("background", new Color(0xF3F5FA));
                    UIManager.put("text", new Color(0x1B1B1B));
                    return;
                }
            }
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fall back to the default look and feel
        }
    }
}
