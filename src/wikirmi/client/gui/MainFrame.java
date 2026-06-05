package wikirmi.client.gui;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import wikirmi.client.ClientCallbackImpl;
import wikirmi.client.WikiEvents;
import wikirmi.client.service.WikiClientController;
import wikirmi.common.dto.LockInfoDTO;
import wikirmi.common.dto.PageDTO;
import wikirmi.common.dto.PageSummaryDTO;
import wikirmi.common.dto.UserDTO;

/** Main application window: list/search pages, view content, and launch edit/history/admin. */
public class MainFrame extends JFrame implements WikiEvents {

    private final WikiClientController controller;
    private final UserDTO user;

    private final JTextField searchField = new JTextField(18);
    private final DefaultTableModel tableModel =
            new DefaultTableModel(new Object[]{"Tytuł", "Wersja", "Edytowana przez"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
    private final JTable pagesTable = new JTable(tableModel);
    private final JTextArea contentArea = new JTextArea();
    private final JLabel statusLabel = new JLabel(" ");

    private List<PageSummaryDTO> currentPages = java.util.Collections.emptyList();
    private String viewedTitle;
    private ClientCallbackImpl callback;

    public MainFrame(WikiClientController controller, UserDTO user) {
        super("WikiRMI — " + user.getUsername() + " (" + user.getRole() + ")");
        this.controller = controller;
        this.user = user;

        buildUi();
        setupCallback();
        reload();

        setSize(820, 520);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { cleanup(); System.exit(0); }
        });
    }

    private void buildUi() {
        // --- top toolbar: search ---
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Szukaj:"));
        top.add(searchField);
        JButton searchBtn = new JButton("Szukaj");
        JButton refreshBtn = new JButton("Odśwież");
        top.add(searchBtn);
        top.add(refreshBtn);
        searchBtn.addActionListener(e -> reload());
        refreshBtn.addActionListener(e -> { searchField.setText(""); reload(); });
        searchField.addActionListener(e -> reload());

        // --- page table (left) ---
        pagesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pagesTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            String title = selectedTitle();
            if (title != null) loadContent(title);
        });
        JScrollPane tableScroll = new JScrollPane(pagesTable);
        tableScroll.setPreferredSize(new Dimension(320, 400));

        // --- content viewer (right) ---
        contentArea.setEditable(false);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane contentScroll = new JScrollPane(contentArea);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, contentScroll);
        split.setResizeWeight(0.4);

        // --- action buttons ---
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton editBtn = new JButton("Edytuj");
        JButton historyBtn = new JButton("Historia");
        JButton logoutBtn = new JButton("Wyloguj");
        actions.add(editBtn);
        actions.add(historyBtn);
        if (controller.isAdmin()) {
            JButton adminBtn = new JButton("Administracja");
            adminBtn.addActionListener(e -> openAdmin());
            actions.add(adminBtn);
        }
        actions.add(logoutBtn);
        editBtn.addActionListener(e -> openEditor());
        historyBtn.addActionListener(e -> openHistory());
        logoutBtn.addActionListener(e -> doLogout());

        JPanel south = new JPanel(new BorderLayout());
        south.add(actions, BorderLayout.WEST);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        south.add(statusLabel, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
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
        for (PageSummaryDTO p : pages) {
            tableModel.addRow(new Object[]{
                    p.getTitle(), p.getVersion(),
                    p.getLockedBy() == null ? "" : p.getLockedBy()});
        }
    }

    private void reselect(String title) {
        if (title == null) return;
        for (int i = 0; i < currentPages.size(); i++) {
            if (currentPages.get(i).getTitle().equals(title)) { pagesTable.setRowSelectionInterval(i, i); return; }
        }
    }

    private void loadContent(String title) {
        UiUtils.async(this, () -> controller.getPage(title), p -> {
            contentArea.setText(p.getContent());
            contentArea.setCaretPosition(0);
            viewedTitle = title;
            String lock = (p.getLock() == null) ? "wolna" : ("edytowana przez " + p.getLock().getHolder());
            statusLabel.setText("Strona: " + p.getTitle() + " · wersja " + p.getVersion()
                    + " · ostatnia zmiana: " + p.getLastEditor() + " (" + UiUtils.formatTime(p.getLastModified()) + ") · " + lock);
        });
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
}
