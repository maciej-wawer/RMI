package wikirmi.client.gui;

import java.awt.*;
import java.util.List;

import javax.swing.*;

import wikirmi.client.service.WikiClientController;
import wikirmi.common.dto.RevisionDTO;

/** Modal dialog: lists a page's revision history; selecting one shows that revision's content. */
public class HistoryDialog extends JDialog {

    private final WikiClientController controller;
    private final String title;
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> revisionList = new JList<>(listModel);
    private final JTextArea contentArea = new JTextArea();
    private List<RevisionDTO> revisions = java.util.Collections.emptyList();

    public HistoryDialog(MainFrame owner, WikiClientController controller, String title) {
        super(owner, "Historia: " + title, true);
        this.controller = controller;
        this.title = title;

        revisionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        revisionList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int i = revisionList.getSelectedIndex();
            if (i >= 0 && i < revisions.size()) showRevision(revisions.get(i).getIndex());
        });
        contentArea.setEditable(false);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        JScrollPane listScroll = new JScrollPane(revisionList);
        listScroll.setPreferredSize(new Dimension(260, 360));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, new JScrollPane(contentArea));
        split.setResizeWeight(0.4);

        setLayout(new BorderLayout());
        add(split, BorderLayout.CENTER);
        setSize(640, 420);
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
            else contentArea.setText("(brak historii — strona nie była jeszcze edytowana)");
        });
    }

    private void showRevision(int index) {
        UiUtils.async(this, () -> controller.getRevision(title, index),
                p -> { contentArea.setText(p.getContent()); contentArea.setCaretPosition(0); });
    }
}
