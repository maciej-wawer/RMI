package wikirmi.client.gui;

import java.awt.*;

import javax.swing.*;

import wikirmi.client.service.WikiClientController;
import wikirmi.common.Role;

/** Clean modal form for creating a user account (admin only). */
public class NewUserDialog extends JDialog {
    public NewUserDialog(JFrame owner, WikiClientController controller, Runnable onCreated) {
        super(owner, "Nowy użytkownik", true);

        JTextField nameField = new JTextField(18);
        JPasswordField passField = new JPasswordField(18);
        JPasswordField confField = new JPasswordField(18);
        JComboBox<Role> roleBox = new JComboBox<>(Role.values());

        JLabel banner = new JLabel("Utwórz konto użytkownika", SwingConstants.LEFT);
        banner.setOpaque(true);
        banner.setBackground(UiUtils.BANNER_BG);
        banner.setForeground(UiUtils.BANNER_FG);
        banner.setFont(banner.getFont().deriveFont(Font.BOLD, 14f));
        banner.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 10));
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        form.add(new JLabel("Login:"));         form.add(nameField);
        form.add(new JLabel("Hasło:"));         form.add(passField);
        form.add(new JLabel("Powtórz hasło:")); form.add(confField);
        form.add(new JLabel("Rola:"));          form.add(roleBox);

        JButton create = new JButton("Utwórz");
        JButton cancel = new JButton("Anuluj");
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(cancel);
        south.add(create);

        setLayout(new BorderLayout());
        add(banner, BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(create);

        cancel.addActionListener(e -> dispose());
        create.addActionListener(e -> {
            final String name = nameField.getText().trim();
            final String pass = new String(passField.getPassword());
            final String conf = new String(confField.getPassword());
            final Role role = (Role) roleBox.getSelectedItem();
            if (name.isEmpty()) { UiUtils.error(this, "Login nie może być pusty."); return; }
            if (pass.length() < 3) { UiUtils.error(this, "Hasło musi mieć co najmniej 3 znaki."); return; }
            if (!pass.equals(conf)) { UiUtils.error(this, "Hasła nie są identyczne."); return; }
            create.setEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception { controller.createUser(name, pass, role); return null; }
                @Override protected void done() {
                    try {
                        get();
                        UiUtils.info(NewUserDialog.this, "Utworzono użytkownika: " + name + " (" + role + ").");
                        if (onCreated != null) onCreated.run();
                        dispose();
                    } catch (Exception ex) {
                        create.setEnabled(true);
                        UiUtils.error(NewUserDialog.this, ex);
                    }
                }
            }.execute();
        });
    }
}
