package wikirmi.client.gui;

import java.awt.*;

import javax.swing.*;

import wikirmi.client.service.WikiClientController;

/** Modal dialog to change the logged-in user's password. */
public class ChangePasswordDialog extends JDialog {
    public ChangePasswordDialog(JFrame owner, WikiClientController controller) {
        super(owner, "Zmień hasło", true);
        JPasswordField oldP = new JPasswordField(16);
        JPasswordField newP = new JPasswordField(16);
        JPasswordField confP = new JPasswordField(16);

        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        form.add(new JLabel("Aktualne hasło:")); form.add(oldP);
        form.add(new JLabel("Nowe hasło:"));     form.add(newP);
        form.add(new JLabel("Powtórz nowe:"));   form.add(confP);

        JButton ok = new JButton("Zmień");
        JButton cancel = new JButton("Anuluj");
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(cancel); south.add(ok);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(ok);

        cancel.addActionListener(e -> dispose());
        ok.addActionListener(e -> {
            final String o = new String(oldP.getPassword());
            final String n = new String(newP.getPassword());
            final String c = new String(confP.getPassword());
            if (!n.equals(c)) { UiUtils.error(this, "Nowe hasła nie są identyczne."); return; }
            ok.setEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception { controller.changePassword(o, n); return null; }
                @Override protected void done() {
                    try { get(); UiUtils.info(ChangePasswordDialog.this, "Hasło zostało zmienione."); dispose(); }
                    catch (Exception ex) { ok.setEnabled(true); UiUtils.error(ChangePasswordDialog.this, ex); }
                }
            }.execute();
        });
    }
}
