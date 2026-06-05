package wikirmi.client;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import wikirmi.client.gui.LoginFrame;

/** Client entry point: shows the login window. Launch several instances to demo concurrency. */
public class WikiClient {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fall back to the default look and feel
        }
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
