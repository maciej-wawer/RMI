package wikirmi.client.gui;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.*;

import wikirmi.client.service.WikiClientController;
import wikirmi.common.dto.*;

/**
 * Modal page editor. The edit-lock is already held when this opens (acquired by the caller).
 * While open it heartbeats the lease ({@code renewEditLock}) and shows a live countdown of the
 * remaining lock time; Save calls {@code savePage}; closing or cancelling releases the lock.
 */
public class EditDialog extends JDialog {

    private static final int HEARTBEAT_MS = 10_000;     // < server lease (30s)
    private static final int LEASE_SECONDS = 30;        // matches ServerConfig.LEASE_MS

    private final WikiClientController controller;
    private final String title;
    private final long baseVersion;
    private final JTextArea editArea = new JTextArea();
    private final JButton saveButton = new JButton("Zapisz");
    private final JLabel countdownLabel = new JLabel();
    private final Timer heartbeat;
    private final Timer countdown;
    private int secondsLeft;
    private boolean closedBySave = false;

    public EditDialog(MainFrame owner, WikiClientController controller, Dto.Page page) {
        super(owner, "Edycja: " + page.getTitle(), true);
        this.controller = controller;
        this.title = page.getTitle();
        this.baseVersion = page.getVersion();
        this.secondsLeft = (page.getLock() != null)
                ? (int) Math.ceil(page.getLock().getRemainingMillis() / 1000.0) : LEASE_SECONDS;

        editArea.setText(page.getContent());
        editArea.setLineWrap(true);
        editArea.setWrapStyleWord(true);
        editArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        editArea.setCaretPosition(0);

        JLabel info = new JLabel("Edytujesz wersję " + baseVersion
                + ". Obsługiwany Markdown: # nagłówek, **pogrubienie**, *kursywa*, - lista, [[Link]].");
        info.setBorder(BorderFactory.createEmptyBorder(6, 8, 0, 8));
        countdownLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 6, 8));
        countdownLabel.setForeground(new Color(0x16660b));
        updateCountdownLabel();
        JPanel north = new JPanel(new BorderLayout());
        north.add(info, BorderLayout.NORTH);
        north.add(countdownLabel, BorderLayout.SOUTH);

        JButton cancelButton = new JButton("Anuluj");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancelButton);
        buttons.add(saveButton);

        setLayout(new BorderLayout());
        add(north, BorderLayout.NORTH);
        add(new JScrollPane(editArea), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setSize(600, 500);
        setLocationRelativeTo(owner);

        saveButton.addActionListener(e -> save());
        cancelButton.addActionListener(e -> cancel());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { cancel(); }
        });

        heartbeat = new Timer(HEARTBEAT_MS, e -> renew());
        heartbeat.start();
        countdown = new Timer(1000, e -> tick());
        countdown.start();
    }

    private void tick() {
        if (secondsLeft > 0) secondsLeft--;
        updateCountdownLabel();
    }

    private void updateCountdownLabel() {
        countdownLabel.setText("Blokada edycji aktywna — wygasa za " + secondsLeft + " s (odnawiana automatycznie).");
    }

    private void renew() {
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception { controller.renewEditLock(title); return null; }
            @Override protected void done() {
                try {
                    get();
                    secondsLeft = LEASE_SECONDS;             // lease extended
                    updateCountdownLabel();
                } catch (Exception ex) {
                    stopTimers();
                    UiUtils.error(EditDialog.this, "Utracono blokadę edycji tej strony. Okno zostanie zamknięte.");
                    dispose();
                }
            }
        }.execute();
    }

    private void save() {
        saveButton.setEnabled(false);
        final String text = editArea.getText();
        new SwingWorker<Dto.Page, Void>() {
            @Override protected Dto.Page doInBackground() throws Exception { return controller.savePage(title, text, baseVersion); }
            @Override protected void done() {
                try {
                    get();
                    closedBySave = true;
                    stopTimers();
                    dispose();
                } catch (Exception ex) {
                    saveButton.setEnabled(true);
                    UiUtils.error(EditDialog.this, ex);
                }
            }
        }.execute();
    }

    private void cancel() {
        stopTimers();
        if (!closedBySave) {
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    try { controller.releaseEditLock(title); } catch (Exception ignored) { }
                    return null;
                }
            }.execute();
        }
        dispose();
    }

    private void stopTimers() {
        heartbeat.stop();
        countdown.stop();
    }
}
