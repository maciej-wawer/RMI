package wikirmi.client.gui;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.*;

import wikirmi.client.service.WikiClientController;
import wikirmi.common.dto.PageDTO;

/**
 * Modal page editor. The edit-lock is already held when this opens (acquired by the caller).
 * While open it heartbeats the lease ({@code renewEditLock}); Save calls {@code savePage}; closing
 * or cancelling releases the lock so others can edit.
 */
public class EditDialog extends JDialog {

    private final WikiClientController controller;
    private final String title;
    private final long baseVersion;
    private final JTextArea editArea = new JTextArea();
    private final JButton saveButton = new JButton("Zapisz");
    private final Timer heartbeat;
    private boolean closedBySave = false;

    private static final int HEARTBEAT_MS = 10_000;     // < server lease (30s)

    public EditDialog(MainFrame owner, WikiClientController controller, PageDTO page) {
        super(owner, "Edycja: " + page.getTitle(), true);
        this.controller = controller;
        this.title = page.getTitle();
        this.baseVersion = page.getVersion();

        editArea.setText(page.getContent());
        editArea.setLineWrap(true);
        editArea.setWrapStyleWord(true);
        editArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        editArea.setCaretPosition(0);

        JLabel info = new JLabel("Edytujesz wersję " + baseVersion + ". Blokada jest aktywna — inni nie mogą teraz zapisywać.");
        info.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JButton cancelButton = new JButton("Anuluj");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancelButton);
        buttons.add(saveButton);

        setLayout(new BorderLayout());
        add(info, BorderLayout.NORTH);
        add(new JScrollPane(editArea), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setSize(560, 460);
        setLocationRelativeTo(owner);

        saveButton.addActionListener(e -> save());
        cancelButton.addActionListener(e -> cancel());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { cancel(); }
        });

        heartbeat = new Timer(HEARTBEAT_MS, e -> renew());
        heartbeat.start();
    }

    private void renew() {
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception { controller.renewEditLock(title); return null; }
            @Override protected void done() {
                try {
                    get();
                } catch (Exception ex) {
                    heartbeat.stop();
                    UiUtils.error(EditDialog.this, "Utracono blokadę edycji tej strony. Okno zostanie zamknięte.");
                    dispose();
                }
            }
        }.execute();
    }

    private void save() {
        saveButton.setEnabled(false);
        final String text = editArea.getText();
        new SwingWorker<PageDTO, Void>() {
            @Override protected PageDTO doInBackground() throws Exception { return controller.savePage(title, text, baseVersion); }
            @Override protected void done() {
                try {
                    get();
                    closedBySave = true;
                    heartbeat.stop();
                    dispose();
                } catch (Exception ex) {
                    saveButton.setEnabled(true);
                    UiUtils.error(EditDialog.this, ex);
                }
            }
        }.execute();
    }

    private void cancel() {
        heartbeat.stop();
        if (!closedBySave) {
            // Best-effort release so the page frees up immediately (don't block closing on it).
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    try { controller.releaseEditLock(title); } catch (Exception ignored) { }
                    return null;
                }
            }.execute();
        }
        dispose();
    }
}
