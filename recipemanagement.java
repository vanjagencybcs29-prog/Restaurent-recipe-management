package recipemanagementpack;
import javax.swing.*;
import java.awt.*;
public class recipemanagement extends JFrame {
    public recipemanagement() {
        setTitle("The Vintage MenuCard");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setExtendedState(MAXIMIZED_BOTH);
        BackgroundPanel bg=new BackgroundPanel("Images/welcome_bg.jpg");
        bg.setLayout(new GridBagLayout());
        JPanel card=new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create();
                g2.setColor(new Color(20, 50, 35, 225));   // <-- box colour
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AppStyle.GOLD, 2), BorderFactory.createEmptyBorder(45, 65, 45, 65)));
        JLabel title=new JLabel("The Vintage MenuCard");
        title.setFont(new Font("Georgia", Font.BOLD, 46));
        title.setForeground(AppStyle.GOLD);
        title.setAlignmentX(.5f);
        JLabel sub=new JLabel("Restaurant Recipe Management System");
        sub.setFont(new Font("Georgia", Font.ITALIC, 20));
        sub.setForeground(AppStyle.CREAM);
        sub.setAlignmentX(.5f);
        JButton login=new JButton("Login"), register=new JButton("Register");
        AppStyle.styleButton(login);
        AppStyle.styleButton(register);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        buttons.setOpaque(false);
        buttons.add(login);
        buttons.add(register);
        card.add(title);
        card.add(Box.createVerticalStrut(18));
        card.add(sub);
        card.add(Box.createVerticalStrut(35));
        card.add(buttons);
        bg.add(card);
        setContentPane(bg);
        login.addActionListener(e-> {
            new LoginPage().setVisible(true);
            dispose();
        });
        register.addActionListener(e-> {
            new RegisterPage().setVisible(true);
            dispose();
        });
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(()->new recipemanagement().setVisible(true));
    }
}
