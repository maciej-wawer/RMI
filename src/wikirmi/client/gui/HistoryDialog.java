package wikirmi.client.gui;

import java.awt.*;
import java.util.Collections;
import java.util.List;

import javax.swing.*;

import wikirmi.client.service.WikiClientController;
import wikirmi.common.dto.RevisionDTO;

/**
 * Modal history browser. Select 1 revision to view its rendered content, or 2 to see a colored
 * diff. The "Przywróć" button restores the selected revision (written as a new revision server-side).
 */
public class HistoryDialog extends JDialog {

    private final WikiClientController controller;
    private final String title;
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> revisionList = new JList<>(listModel);
    private final JEditorPane viewer = new JEditorPane();
    private final JButton restoreButton = new JButton("Przywróć tę wersję");
    private List<RevisionDTO> revisions = Collections.emptyList();

    public HistoryDialog(MainFrame owner, WikiClientController controller, String title) {
        super(owner, "Historia: " + title, true);
        this.controller = controller;
        this.title = title;

        revisionList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        revisionList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) onSelection(); });
        viewer.setContentType("text/html");
        viewer.setEditable(false);

        JScrollPane listScroll = new JScrollPane(revisionList);
        listScroll.setPreferredSize(new Dimension(270, 360));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, new JScrollPane(viewer));
        split.setResizeWeight(0.4);

        JLabel hint = new JLabel("Zaznacz 1 rewizję, aby ją zobaczyć, lub 2 (Ctrl), aby porównać różnice.");
        hint.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        restoreButton.setEnabled(false);
        restoreButton.addActionListener(e -> restore());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(restoreButton);

        setLayout(new BorderLayout());
        add(hint, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setSize(740, 480);
        setLocationRelativeTo(owner);

        load();
    }

    private void load() {
        UiUtils.async(this, () -> controller.getHistory(title), hist -> {
            revisions = hist;
            listModel.clear();
            for (RevisionDTO r : hist) {
                listModel.addElement("v" + r.getIndex() + " — " + r.getEditor() + " — " + UiUtils.formatTime(r.getTimestamp()));
            }
            if (!hist.isEmpty()) revisionList.setSelectedIndex(hist.size() - 1);
            else viewer.setText("<html><body>(brak historii — strona nie była jeszcze edytowana)</body></html>");
        });
    }

    private void onSelection() {
        int[] sel = revisionList.getSelectedIndices();
        restoreButton.setEnabled(sel.length == 1);
        if (sel.length == 1) {
            viewer.setText(MarkdownRenderer.toHtml(revisions.get(sel[0]).getContent()));
            viewer.setCaretPosition(0);
        } else if (sel.length == 2) {
            RevisionDTO a = revisions.get(Math.min(sel[0], sel[1]));
            RevisionDTO b = revisions.get(Math.max(sel[0], sel[1]));
            viewer.setText(TextDiff.toHtml(a.getContent(), b.getContent()));
            viewer.setCaretPosition(0);
        } else {
            viewer.setText("<html><body>Zaznacz 1 lub 2 rewizje.</body></html>");
        }
    }

    private void restore() {
        int[] sel = revisionList.getSelectedIndices();
        if (sel.length != 1) return;
        final RevisionDTO r = revisions.get(sel[0]);
        if (!UiUtils.confirm(this, "Przywrócić wersję v" + r.getIndex() + "? Zostanie zapisana jako nowa wersja.")) return;
        restoreButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception { controller.restoreRevision(title, r.getIndex()); return null; }
            @Override protected void done() {
                try { get(); UiUtils.info(HistoryDialog.this, "Przywrócono wersję v" + r.getIndex() + "."); dispose(); }
                catch (Exception ex) { restoreButton.setEnabled(true); UiUtils.error(HistoryDialog.this, ex); }
            }
        }.execute();
    }
}
