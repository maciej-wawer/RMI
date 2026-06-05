package wikirmi.client.gui;

import java.awt.*;

import javax.swing.*;

import wikirmi.client.service.WikiClientController;

/** Modal dialog for creating a new page (title + initial content). Available to any logged-in user. */
public class NewPageDialog extends JDialog {
    private boolean created = false;
    private String createdTitle;

    public NewPageDialog(MainFrame owner, WikiClientController controller) {
        super(owner, "Nowa strona", true);

        JTextField titleField = new JTextField(28);
        JTextArea contentArea = new JTextArea(10, 28);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);

        JPanel top = new JPanel(new BorderLayout(6, 6));
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        titleRow.add(new JLabel("Tytuł:"));
        titleRow.add(titleField);
        top.add(titleRow, BorderLayout.NORTH);
        top.add(new JLabel("Treść (Markdown: # nagłówek, **pogrubienie**, - lista, [[Link]]):"), BorderLayout.SOUTH);

        JButton create = new JButton("Utwórz");
        JButton cancel = new JButton("Anuluj");
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(cancel);
        south.add(create);

        setLayout(new BorderLayout(0, 6));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(contentArea), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(create);

        cancel.addActionListener(e -> dispose());
        create.addActionListener(e -> {
            final String title = titleField.getText().trim();
            final String content = contentArea.getText();
            create.setEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception { controller.createPage(title, content); return null; }
                @Override protected void done() {
                    try { get(); created = true; createdTitle = title; dispose(); }
                    catch (Exception ex) { create.setEnabled(true); UiUtils.error(NewPageDialog.this, ex); }
                }
            }.execute();
        });
    }

    public boolean isCreated() { return created; }
    public String getCreatedTitle() { return createdTitle; }
}
