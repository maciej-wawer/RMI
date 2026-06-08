package wikirmi.client.gui;

import java.awt.*;

import javax.swing.*;

import wikirmi.client.service.WikiClientController;
import wikirmi.common.Protocol;
import wikirmi.common.dto.*;

/** Connection + login window. On success it opens the {@link MainFrame}. */
public class LoginFrame extends JFrame {
    private final WikiClientController controller = new WikiClientController();
    private final JTextField hostField = new JTextField("localhost");
    private final JTextField portField = new JTextField(String.valueOf(Protocol.DEFAULT_REGISTRY_PORT));
    private final JTextField userField = new JTextField("admin");
    private final JPasswordField passField = new JPasswordField("admin123");
    private final JButton loginButton = new JButton("Połącz i zaloguj");

    public LoginFrame() {
        super("WikiRMI — logowanie");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JLabel banner = new JLabel("WikiRMI", SwingConstants.CENTER);
        banner.setOpaque(true);
        banner.setBackground(UiUtils.BANNER_BG);
        banner.setForeground(UiUtils.BANNER_FG);
        banner.setFont(banner.getFont().deriveFont(Font.BOLD, 22f));
        banner.setBorder(BorderFactory.createEmptyBorder(14, 0, 14, 0));

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(18, 18, 8, 18));
        form.add(new JLabel("Serwer:"));   form.add(hostField);
        form.add(new JLabel("Port:"));     form.add(portField);
        form.add(new JLabel("Użytkownik:")); form.add(userField);
        form.add(new JLabel("Hasło:"));    form.add(passField);

        JPanel south = new JPanel();
        south.add(loginButton);

        setLayout(new BorderLayout());
        add(banner, BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setSize(400, 300);
        UiUtils.placeWindow(this);

        getRootPane().setDefaultButton(loginButton);
        loginButton.addActionListener(e -> doLogin());
    }

    private void doLogin() {
        final String host = hostField.getText().trim();
        final String portText = portField.getText().trim();
        final String username = userField.getText().trim();
        final String password = new String(passField.getPassword());
        final int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException ex) {
            UiUtils.error(this, "Port musi być liczbą.");
            return;
        }
        loginButton.setEnabled(false);
        new SwingWorker<Dto.User, Void>() {
            @Override protected Dto.User doInBackground() throws Exception {
                controller.connect(host, port);
                return controller.login(username, password);
            }
            @Override protected void done() {
                loginButton.setEnabled(true);
                try {
                    Dto.User user = get();
                    new MainFrame(controller, user).setVisible(true);
                    dispose();
                } catch (Exception ex) {
                    UiUtils.error(LoginFrame.this, ex);
                }
            }
        }.execute();
    }
}
