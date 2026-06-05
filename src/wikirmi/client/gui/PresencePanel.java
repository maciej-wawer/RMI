package wikirmi.client.gui;

import java.awt.*;
import java.util.List;

import javax.swing.*;

/** Right-side sidebar: who is online and which pages are currently being edited. Pure view. */
public class PresencePanel extends JPanel {
    private final DefaultListModel<String> onlineModel = new DefaultListModel<>();
    private final DefaultListModel<String> editsModel = new DefaultListModel<>();

    public PresencePanel() {
        super(new GridLayout(2, 1, 4, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(titled("Online", new JList<>(onlineModel)));
        add(titled("Aktualnie edytowane", new JList<>(editsModel)));
        setPreferredSize(new Dimension(210, 400));
    }

    private JPanel titled(String title, JList<String> list) {
        list.setEnabled(false);                         // display-only
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(new JScrollPane(list), BorderLayout.CENTER);
        return p;
    }

    public void setOnline(List<String> users) {
        onlineModel.clear();
        for (String u : users) onlineModel.addElement(u);
    }

    public void setActiveEdits(List<String> edits) {
        editsModel.clear();
        if (edits.isEmpty()) editsModel.addElement("(nikt nie edytuje)");
        else for (String e : edits) editsModel.addElement(e);
    }
}
