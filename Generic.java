package recipemanagementpack;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.Collections;

/*
 * GENERIC FORM TEMPLATE  (Java Swing + Oracle)
 * Works for ANY table. Change only the 5 lines in STEP 1.
 * Buttons: Add, Update, Delete, Clear. Table below shows all rows.
 * Click a row in the table -> its values come into the text boxes.
 */
public class Generic extends JFrame {

    // ============ STEP 1 : CHANGE ONLY THESE 5 THINGS ============
	static final String   FORM_TITLE = "Feedback Form";
	static final String   TABLE_NAME = "FEEDBACK";
	static final String   ID_COLUMN  = "FEEDBACK_ID";
	static final String[] LABELS     = {"Customer Name", "Dish Name", "Rating (1-5)", "Comments"};
	static final String[] COLUMNS    = {"CUSTOMER_NAME", "DISH_NAME", "RATING", "COMMENTS"};// column names in the table
    // (LABELS and COLUMNS must be in the same order and same count)
    // =============================================================

    private final JTextField[] fields = new JTextField[LABELS.length];
    private final DefaultTableModel model = new DefaultTableModel();
    private final JTable table = new JTable(model);

    public Generic() {
        setTitle(FORM_TITLE);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        // ---- header of the table: ID + labels ----
        model.addColumn("ID");
        for (String l : LABELS) model.addColumn(l);

        // ---- text boxes ----
        JPanel form = new JPanel(new GridLayout(LABELS.length, 2, 10, 10));
        form.setBackground(AppStyle.CREAM);
        form.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        for (int i = 0; i < LABELS.length; i++) {
            JLabel lb = new JLabel(LABELS[i]);
            lb.setFont(new Font("Segoe UI", Font.BOLD, 14));
            fields[i] = new JTextField();
            form.add(lb);
            form.add(fields[i]);
        }

        // ---- buttons ----
        JButton add = new JButton("Add");
        JButton update = new JButton("Update");
        JButton delete = new JButton("Delete");
        JButton clear = new JButton("Clear");
        for (JButton b : new JButton[]{add, update, delete, clear}) AppStyle.styleButton(b);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 8));
        buttons.setBackground(AppStyle.BG);
        buttons.add(add);
        buttons.add(update);
        buttons.add(delete);
        buttons.add(clear);

        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.CENTER);
        top.add(buttons, BorderLayout.SOUTH);

        // ---- table ----
        AppStyle.styleTable(table);

        // ---- put everything together ----
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBackground(AppStyle.BG);
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        main.add(AppStyle.heading(FORM_TITLE), BorderLayout.NORTH);
        main.add(top, BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(800, 250));
        main.add(scroll, BorderLayout.SOUTH);
        setContentPane(main);

        // ---- button actions ----
        add.addActionListener(e -> addRecord());
        update.addActionListener(e -> updateRecord());
        delete.addActionListener(e -> deleteRecord());
        clear.addActionListener(e -> clearFields());

        // click a row -> fill text boxes
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0 && !e.getValueIsAdjusting()) {
                for (int i = 0; i < fields.length; i++) {
                    Object v = model.getValueAt(row, i + 1);
                    fields[i].setText(v == null ? "" : v.toString());
                }
            }
        });

        loadData();
    }

    // ================= SHOW ALL ROWS =================
    private void loadData() {
        model.setRowCount(0);
        String sql = "SELECT " + ID_COLUMN + "," + String.join(",", COLUMNS)
                + " FROM " + TABLE_NAME + " ORDER BY " + ID_COLUMN;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Object[] row = new Object[COLUMNS.length + 1];
                for (int i = 0; i < row.length; i++) row[i] = rs.getString(i + 1);
                model.addRow(row);
            }
        } catch (SQLException ex) {
            msg("Load error: " + ex.getMessage());
        }
    }

    // ================= ADD =================
    private void addRecord() {
        if (!allFilled()) return;
        String marks = String.join(",", Collections.nCopies(COLUMNS.length, "?"));
        String sql = "INSERT INTO " + TABLE_NAME + " (" + String.join(",", COLUMNS) + ") VALUES (" + marks + ")";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < fields.length; i++) ps.setString(i + 1, fields[i].getText().trim());
            ps.executeUpdate();
            msg("Added successfully!");
            clearFields();
            loadData();
        } catch (SQLException ex) {
            msg("Add error: " + ex.getMessage());
        }
    }

    // ================= UPDATE =================
    private void updateRecord() {
        String id = selectedId();
        if (id == null || !allFilled()) return;
        StringBuilder set = new StringBuilder();
        for (int i = 0; i < COLUMNS.length; i++) {
            if (i > 0) set.append(",");
            set.append(COLUMNS[i]).append("=?");
        }
        String sql = "UPDATE " + TABLE_NAME + " SET " + set + " WHERE " + ID_COLUMN + "=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < fields.length; i++) ps.setString(i + 1, fields[i].getText().trim());
            ps.setString(fields.length + 1, id);
            ps.executeUpdate();
            msg("Updated successfully!");
            clearFields();
            loadData();
        } catch (SQLException ex) {
            msg("Update error: " + ex.getMessage());
        }
    }

    // ================= DELETE =================
    private void deleteRecord() {
        String id = selectedId();
        if (id == null) return;
        if (JOptionPane.showConfirmDialog(this, "Delete this record?", "Confirm",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE " + ID_COLUMN + "=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
            msg("Deleted successfully!");
            clearFields();
            loadData();
        } catch (SQLException ex) {
            msg("Delete error: " + ex.getMessage());
        }
    }

    // ================= SMALL HELPERS =================
    private String selectedId() {
        int row = table.getSelectedRow();
        if (row < 0) {
            msg("Please select a row from the table first.");
            return null;
        }
        return model.getValueAt(row, 0).toString();
    }

    private boolean allFilled() {
        for (JTextField f : fields) {
            if (f.getText().trim().isEmpty()) {
                msg("Please fill in every field.");
                return false;
            }
        }
        return true;
    }

    private void clearFields() {
        for (JTextField f : fields) f.setText("");
        table.clearSelection();
    }

    private void msg(String s) {
        JOptionPane.showMessageDialog(this, s);
    }

    // run this file directly to test
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Generic().setVisible(true));
    }
}