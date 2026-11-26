package recipemanagementpack;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
public final class AppStyle {
    public static final Color BG=new Color(29, 27, 24), PANEL=new Color(48, 43, 36), GOLD=new Color(212, 175, 55), CREAM=new Color(246, 237, 220), BROWN=new Color(91, 58, 39);
    private AppStyle(){}
            public static void styleButton(JButton b) {
        b.setBackground(GOLD);
        b.setForeground(BG);
        b.setFont(new Font("Serif", Font.BOLD, 14));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    public static void styleTable(JTable t) {
        t.setRowHeight(26);
        t.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        t.setBackground(CREAM);
        t.setForeground(BROWN);
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        t.getTableHeader().setBackground(GOLD);
    }
    public static JLabel heading(String s) {
        JLabel l=new JLabel(s, SwingConstants.CENTER);
        l.setFont(new Font("Serif", Font.BOLD, 27));
        l.setForeground(GOLD);
        l.setBorder(BorderFactory.createEmptyBorder(15, 5, 15, 5));
        return l;
    }
}
