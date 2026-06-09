package wikirmi.client.gui;

import java.awt.*;

import javax.swing.*;

import wikirmi.client.service.WikiClientController;
import wikirmi.common.dto.*;

/** Panel administracyjny: zarządzanie kontami i stronami (tworzenie w osobnych, czytelnych oknach). */
public class AdminDialog extends JDialog {

    private final MainFrame owner;
    private final WikiClientController controller;
    private final DefaultListModel<String> usersModel = new DefaultListModel<>();
    private final DefaultListModel<String> pagesModel = new DefaultListModel<>();
    private final JList<String> usersList = new JList<>(usersModel);
    private final JList<String> pagesList = new JList<>(pagesModel);

    public AdminDialog(MainFrame owner, WikiClientController controller) {
        super(owner, "Administracja", true);
        this.owner = owner;
        this.controller = controller;

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Użytkownicy", buildTab(usersList,
                "Nowy użytkownik…", this::openNewUser,
                "Usuń użytkownika", this::deleteSelectedUser));
        tabs.addTab("Strony", buildTab(pagesList,
                "Nowa strona…", this::openNewPage,
                "Usuń stronę", this::deleteSelectedPage));

        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
        setSize(520, 460);
        setMinimumSize(new Dimension(440, 360));
        setLocationRelativeTo(owner);

        refreshUsers();
        refreshPages();
    }

    /** Zakładka: lista (na środku) + dwa przyciski w osobnym rzędzie na dole (nigdy się nie zakrywają). */
    private JComponent buildTab(JList<String> list, String addText, Runnable addAction, String delText, Runnable delAction) {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JButton add = new JButton(addText);
        JButton del = new JButton(delText);
        add.addActionListener(e -> addAction.run());
        del.addActionListener(e -> delAction.run());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(del);
        buttons.add(add);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    // ---------------------------------------------------------------- użytkownicy
    private void openNewUser() {
        new NewUserDialog(owner, controller, this::refreshUsers).setVisible(true);
    }

    private void deleteSelectedUser() {
        String sel = usersList.getSelectedValue();
        if (sel == null) { UiUtils.info(this, "Wybierz użytkownika do usunięcia."); return; }
        final String name = sel.split(" ")[0];     // "login (ROLA)" -> login
        if (!UiUtils.confirm(this, "Usunąć użytkownika '" + name + "'?")) return;
        UiUtils.async(this, () -> { controller.deleteUser(name); return null; }, v -> refreshUsers());
    }

    private void refreshUsers() {
        UiUtils.async(this, () -> controller.listUsers(), users -> {
            usersModel.clear();
            for (Dto.User u : users) usersModel.addElement(u.getUsername() + "  (" + u.getRole() + ")");
        });
    }

    // ---------------------------------------------------------------- strony
    private void openNewPage() {
        NewPageDialog dlg = new NewPageDialog(owner, controller);
        dlg.setVisible(true);
        if (dlg.isCreated()) refreshPages();
    }

    private void deleteSelectedPage() {
        String title = pagesList.getSelectedValue();
        if (title == null) { UiUtils.info(this, "Wybierz stronę do usunięcia."); return; }
        if (!UiUtils.confirm(this, "Usunąć stronę '" + title + "'?")) return;
        UiUtils.async(this, () -> { controller.deletePage(title); return null; }, v -> refreshPages());
    }

    private void refreshPages() {
        UiUtils.async(this, () -> controller.listPages(), pages -> {
            pagesModel.clear();
            for (Dto.PageSummary p : pages) pagesModel.addElement(p.getTitle());
        });
    }
}
