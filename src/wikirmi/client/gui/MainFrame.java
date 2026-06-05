package wikirmi.client.gui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.DefaultTableModel;

import wikirmi.client.ClientCallbackImpl;
import wikirmi.client.WikiEvents;
import wikirmi.client.service.WikiClientController;
import wikirmi.common.dto.LockInfoDTO;
import wikirmi.common.dto.PageSummaryDTO;
import wikirmi.common.dto.UserDTO;

/** Main application window: menu/toolbar, page list, rendered content, live presence, status bar. */
public class MainFrame extends JFrame implements WikiEvents {

    private final WikiClientController controller;
    private final UserDTO user;

    private final JTextField searchField = new JTextField(16);
    private final DefaultTableModel tableModel =
            new DefaultTableModel(new Object[]{"Tytuł", "Wersja", "Edytowana przez"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
    private final JTable pagesTable = new JTable(tableModel);
    private final JEditorPane content = new JEditorPane();
    private final PresencePanel presencePanel = new PresencePanel();
    private final JLabel statusConn = new JLabel(" ");
    private final JLabel statusPage = new JLabel(" ");

    private List<PageSummaryDTO> currentPages = java.util.Collections.emptyList();
    private String viewedTitle;
    private ClientCallbackImpl callback;

    public MainFrame(WikiClientController controller, UserDTO user) {
        super("WikiRMI — " + user.getUsername() + " (" + user.getRole() + ")");
        this.controller = controller;
        this.user = user;

        setJMenuBar(buildMenuBar());
        buildUi();
        installShortcuts();
        setupCallback();
        reload();
        refreshOnline();
        updateStatusConn(0);

        setSize(960, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { cleanup(); System.exit(0); }
        });
    }

    // ---------------------------------------------------------------- UI
    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("Plik");
        file.add(menuItem("Odśwież", "F5", this::reload));
        file.addSeparator();
        file.add(menuItem("Wyloguj", null, this::doLogout));
        file.add(menuItem("Zakończ", null, () -> { cleanup(); System.exit(0); }));
        bar.add(file);

        JMenu edit = new JMenu("Edycja");
        edit.add(menuItem("Edytuj stronę", "control E", this::openEditor));
        edit.add(menuItem("Historia / wersje", "control H", this::openHistory));
        bar.add(edit);

        JMenu account = new JMenu("Konto");
        account.add(menuItem("Zmień hasło", null, this::changePassword));
        bar.add(account);

        if (controller.isAdmin()) {
            JMenu admin = new JMenu("Administracja");
            admin.add(menuItem("Panel administracyjny", null, this::openAdmin));
            admin.add(menuItem("Wymuś odblokowanie strony", null, this::forceUnlock));
            bar.add(admin);
        }

        JMenu help = new JMenu("Pomoc");
        help.add(menuItem("O programie", null, this::showAbout));
        bar.add(help);
        return bar;
    }

    private JMenuItem menuItem(String text, String accelerator, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        if (accelerator != null) item.setAccelerator(KeyStroke.getKeyStroke(accelerator));
        item.addActionListener(e -> action.run());
        return item;
    }

    private void buildUi() {
        // toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(new JLabel("Szukaj: "));
        toolbar.add(searchField);
        toolbar.add(button("Szukaj", this::reload));
        toolbar.add(button("Odśwież", () -> { searchField.setText(""); reload(); }));
        toolbar.addSeparator();
        toolbar.add(button("Edytuj", this::openEditor));
        toolbar.add(button("Historia", this::openHistory));
        if (controller.isAdmin()) {
            toolbar.addSeparator();
            toolbar.add(button("Administracja", this::openAdmin));
            toolbar.add(button("Wymuś odblokowanie", this::forceUnlock));
        }
        searchField.addActionListener(e -> reload());

        // page table
        pagesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pagesTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            String title = selectedTitle();
            if (title != null) loadContent(title);
        });
        JScrollPane tableScroll = new JScrollPane(pagesTable);

        // content viewer (rendered HTML + clickable [[links]])
        content.setContentType("text/html");
        content.setEditable(false);
        content.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                String page = MarkdownRenderer.pageFromLink(e.getDescription());
                if (page != null) navigateTo(page);
            }
        });
        JScrollPane contentScroll = new JScrollPane(content);

        JSplitPane innerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, contentScroll, presencePanel);
        innerSplit.setResizeWeight(0.74);
        JSplitPane outerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, innerSplit);
        outerSplit.setResizeWeight(0.30);

        // status bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        statusBar.add(statusConn, BorderLayout.WEST);
        statusBar.add(statusPage, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(toolbar, BorderLayout.NORTH);
        add(outerSplit, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
    }

    private JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void installShortcuts() {
        JRootPane rp = getRootPane();
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control F"), "focusSearch");
        rp.getActionMap().put("focusSearch", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { searchField.requestFocusInWindow(); }
        });
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control R"), "refresh");
        rp.getActionMap().put("refresh", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { reload(); }
        });
    }

    private void setupCallback() {
        try {
            callback = new ClientCallbackImpl(this);
        } catch (RemoteException ex) {
            UiUtils.error(this, "Nie udało się włączyć powiadomień na żywo: " + ex.getMessage());
            return;
        }
        UiUtils.async(this, () -> { controller.subscribe(callback); return null; }, v -> { });
    }

    // ---------------------------------------------------------------- data
    private void reload() {
        final String sel = selectedTitle();
        UiUtils.async(this,
                () -> {
                    String q = searchField.getText().trim();
                    return q.isEmpty() ? controller.listPages() : controller.searchPages(q);
                },
                pages -> { setPages(pages); reselect(sel); });
    }

    private void setPages(List<PageSummaryDTO> pages) {
        currentPages = pages;
        tableModel.setRowCount(0);
        List<String> activeEdits = new ArrayList<>();
        for (PageSummaryDTO p : pages) {
            tableModel.addRow(new Object[]{p.getTitle(), p.getVersion(), p.getLockedBy() == null ? "" : p.getLockedBy()});
            if (p.getLockedBy() != null) activeEdits.add(p.getTitle() + " — " + p.getLockedBy());
        }
        presencePanel.setActiveEdits(activeEdits);
    }

    private void reselect(String title) {
        if (title == null) return;
        for (int i = 0; i < currentPages.size(); i++) {
            if (currentPages.get(i).getTitle().equals(title)) { pagesTable.setRowSelectionInterval(i, i); return; }
        }
    }

    private void loadContent(String title) {
        UiUtils.async(this, () -> controller.getPage(title), p -> {
            content.setText(MarkdownRenderer.toHtml(p.getContent()));
            content.setCaretPosition(0);
            viewedTitle = title;
            String lock = (p.getLock() == null) ? "wolna" : ("edytowana przez " + p.getLock().getHolder());
            statusPage.setText("Strona: " + p.getTitle() + " · wersja " + p.getVersion()
                    + " · " + p.getLastEditor() + " · " + lock + "   ");
        });
    }

    private void navigateTo(String title) {
        for (int i = 0; i < currentPages.size(); i++) {
            if (currentPages.get(i).getTitle().equals(title)) {
                pagesTable.setRowSelectionInterval(i, i);
                pagesTable.scrollRectToVisible(pagesTable.getCellRect(i, 0, true));
                return;
            }
        }
        loadContent(title);     // not in the current (possibly filtered) list — load directly
    }

    private void refreshOnline() {
        UiUtils.async(this, () -> controller.listOnlineUsers(), users -> {
            presencePanel.setOnline(users);
            updateStatusConn(users.size());
        });
    }

    private void updateStatusConn(int online) {
        statusConn.setText("Połączono: " + controller.serverEndpoint()
                + "   |   Użytkownik: " + user.getUsername() + " (" + user.getRole() + ")"
                + "   |   Online: " + online);
    }

    private String selectedTitle() {
        int row = pagesTable.getSelectedRow();
        return (row >= 0 && row < currentPages.size()) ? currentPages.get(row).getTitle() : null;
    }

    // ---------------------------------------------------------------- actions
    private void openEditor() {
        final String title = selectedTitle();
        if (title == null) { UiUtils.info(this, "Najpierw wybierz stronę z listy."); return; }
        UiUtils.async(this,
                () -> { controller.acquireEditLock(title); return controller.getPage(title); },
                page -> {
                    new EditDialog(this, controller, page).setVisible(true);   // modal
                    reload();
                    if (title.equals(viewedTitle)) loadContent(title);
                });
    }

    private void openHistory() {
        final String title = selectedTitle();
        if (title == null) { UiUtils.info(this, "Najpierw wybierz stronę z listy."); return; }
        new HistoryDialog(this, controller, title).setVisible(true);
    }

    private void openAdmin() {
        new AdminDialog(this, controller).setVisible(true);
        reload();
    }

    private void forceUnlock() {
        final String title = selectedTitle();
        if (title == null) { UiUtils.info(this, "Wybierz stronę do odblokowania."); return; }
        if (!UiUtils.confirm(this, "Wymusić odblokowanie strony '" + title + "'?")) return;
        UiUtils.async(this, () -> { controller.forceUnlock(title); return null; }, v -> { });
    }

    private void changePassword() {
        new ChangePasswordDialog(this, controller).setVisible(true);
    }

    private void showAbout() {
        UiUtils.info(this, "WikiRMI — rozproszony system wiki (Java RMI)\n"
                + "Edycja z blokadą, powiadomienia na żywo, historia wersji, Markdown.\n"
                + "Serwer: " + controller.serverEndpoint());
    }

    private void doLogout() {
        cleanup();
        new LoginFrame().setVisible(true);
        dispose();
    }

    private void cleanup() {
        if (callback != null) {
            try { controller.unsubscribe(callback); } catch (Exception ignored) { }
            try { UnicastRemoteObject.unexportObject(callback, true); } catch (Exception ignored) { }
        }
        controller.logout();
    }

    // ---------------------------------------------------------------- WikiEvents (server push, on EDT)
    @Override public void pageCreated(PageSummaryDTO page) { reload(); }
    @Override public void pageChanged(PageSummaryDTO page) {
        reload();
        if (page.getTitle().equals(viewedTitle)) loadContent(viewedTitle);
    }
    @Override public void pageDeleted(String title) { reload(); }
    @Override public void lockChanged(String title, LockInfoDTO lock) { reload(); }
    @Override public void presenceChanged() { refreshOnline(); }
}
