package wikirmi.client.gui;

import java.awt.*;
import java.util.List;

import javax.swing.*;

import wikirmi.client.service.WikiClientController;
import wikirmi.common.Role;
import wikirmi.common.dto.*;

/** Modal admin console: create/delete pages (skeletons) and create/delete user accounts. */
public class AdminDialog extends JDialog {

    private final WikiClientController controller;

    // pages tab
    private final JTextField pageTitleField = new JTextField(20);
    private final JTextArea pageContentArea = new JTextArea(5, 20);
    private final DefaultListModel<String> pagesModel = new DefaultListModel<>();
    private final JList<String> pagesList = new JList<>(pagesModel);

    // users tab
    private final JTextField userNameField = new JTextField(14);
    private final JPasswordField userPassField = new JPasswordField(14);
    private final JComboBox<Role> roleBox = new JComboBox<>(Role.values());
    private final DefaultListModel<String> usersModel = new DefaultListModel<>();
    private final JList<String> usersList = new JList<>(usersModel);

    public AdminDialog(MainFrame owner, WikiClientController controller) {
        super(owner, "Administracja", true);
        this.controller = controller;

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Strony", buildPagesTab());
        tabs.addTab("Użytkownicy", buildUsersTab());
        setContentPane(tabs);
        setSize(560, 460);
        setLocationRelativeTo(owner);

        refreshPages();
        refreshUsers();
    }

    // ---------------------------------------------------------------- pages tab
    private JComponent buildPagesTab() {
        JPanel create = new JPanel(new BorderLayout(6, 6));
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.add(new JLabel("Tytuł:"));
        form.add(pageTitleField);
        JButton createBtn = new JButton("Utwórz stronę");
        form.add(createBtn);
        pageContentArea.setLineWrap(true);
        pageContentArea.setWrapStyleWord(true);
        create.add(form, BorderLayout.NORTH);
        create.add(new JScrollPane(pageContentArea), BorderLayout.CENTER);
        create.setBorder(BorderFactory.createTitledBorder("Nowa strona (szkielet)"));

        JPanel manage = new JPanel(new BorderLayout(6, 6));
        pagesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JButton deleteBtn = new JButton("Usuń zaznaczoną stronę");
        manage.add(new JScrollPane(pagesList), BorderLayout.CENTER);
        manage.add(deleteBtn, BorderLayout.SOUTH);
        manage.setBorder(BorderFactory.createTitledBorder("Istniejące strony"));

        createBtn.addActionListener(e -> {
            final String title = pageTitleField.getText().trim();
            final String content = pageContentArea.getText();
            UiUtils.async(this, () -> { controller.createPage(title, content); return null; }, v -> {
                pageTitleField.setText(""); pageContentArea.setText("");
                UiUtils.info(this, "Utworzono stronę: " + title);
                refreshPages();
            });
        });
        deleteBtn.addActionListener(e -> {
            String title = pagesList.getSelectedValue();
            if (title == null) { UiUtils.info(this, "Wybierz stronę do usunięcia."); return; }
            if (!UiUtils.confirm(this, "Usunąć stronę '" + title + "'?")) return;
            UiUtils.async(this, () -> { controller.deletePage(title); return null; }, v -> refreshPages());
        });

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, create, manage);
        split.setResizeWeight(0.5);
        return split;
    }

    private void refreshPages() {
        UiUtils.async(this, () -> controller.listPages(), pages -> {
            pagesModel.clear();
            for (Dto.PageSummary p : pages) pagesModel.addElement(p.getTitle());
        });
    }

    // ---------------------------------------------------------------- users tab
    private JComponent buildUsersTab() {
        JPanel create = new JPanel(new FlowLayout(FlowLayout.LEFT));
        create.add(new JLabel("Login:"));
        create.add(userNameField);
        create.add(new JLabel("Hasło:"));
        create.add(userPassField);
        create.add(new JLabel("Rola:"));
        create.add(roleBox);
        JButton createBtn = new JButton("Utwórz użytkownika");
        create.add(createBtn);
        create.setBorder(BorderFactory.createTitledBorder("Nowy użytkownik"));

        JPanel manage = new JPanel(new BorderLayout(6, 6));
        usersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JButton deleteBtn = new JButton("Usuń zaznaczonego użytkownika");
        manage.add(new JScrollPane(usersList), BorderLayout.CENTER);
        manage.add(deleteBtn, BorderLayout.SOUTH);
        manage.setBorder(BorderFactory.createTitledBorder("Istniejący użytkownicy"));

        createBtn.addActionListener(e -> {
            final String name = userNameField.getText().trim();
            final String pass = new String(userPassField.getPassword());
            final Role role = (Role) roleBox.getSelectedItem();
            UiUtils.async(this, () -> { controller.createUser(name, pass, role); return null; }, v -> {
                userNameField.setText(""); userPassField.setText("");
                UiUtils.info(this, "Utworzono użytkownika: " + name);
                refreshUsers();
            });
        });
        deleteBtn.addActionListener(e -> {
            String sel = usersList.getSelectedValue();
            if (sel == null) { UiUtils.info(this, "Wybierz użytkownika do usunięcia."); return; }
            final String name = sel.split(" ")[0];     // "name (ROLE)" -> name
            if (!UiUtils.confirm(this, "Usunąć użytkownika '" + name + "'?")) return;
            UiUtils.async(this, () -> { controller.deleteUser(name); return null; }, v -> refreshUsers());
        });

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(create, BorderLayout.NORTH);
        panel.add(manage, BorderLayout.CENTER);
        return panel;
    }

    private void refreshUsers() {
        UiUtils.async(this, () -> controller.listUsers(), users -> {
            usersModel.clear();
            for (Dto.User u : users) usersModel.addElement(u.getUsername() + " (" + u.getRole() + ")");
        });
    }
}
