package recipemanagementpack;
import javax.swing.*;
import java.awt.*;
import java.sql.*;
public class LoginPage extends JFrame {
    private final JTextField username=new JTextField();
    private final JPasswordField password=new JPasswordField();
    private final JComboBox<String> role=new JComboBox<>(new String[]{"CUSTOMER", "CHEF", "HOTEL MANAGER"});
    public LoginPage() {
        setTitle("Login");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setExtendedState(MAXIMIZED_BOTH);
        BackgroundPanel outer=new BackgroundPanel("Images/welcome_bg.jpg");
        outer.setLayout(new GridBagLayout());
        outer.setBackground(AppStyle.BG);
        JPanel card=new JPanel(new GridLayout(4, 2, 12, 18));
        card.setBackground(AppStyle.CREAM);
        card.setBorder(BorderFactory.createEmptyBorder(35, 40, 35, 40));
        card.setPreferredSize(new Dimension(500, 300));
        card.add(new JLabel("Login as"));
        card.add(role);
        card.add(new JLabel("Username"));
        card.add(username);
        card.add(new JLabel("Password"));
        card.add(password);
        JButton login=new JButton("Login"), back=new JButton("Back");
        AppStyle.styleButton(login);
        AppStyle.styleButton(back);
        card.add(login);
        card.add(back);
        outer.add(card);
        setContentPane(outer);
        login.addActionListener(e->login());
        back.addActionListener(e-> {
            new recipemanagement().setVisible(true);
            dispose();
        });
    }
    private void login() {
        String u=username.getText().trim(), p=new String(password.getPassword()), r=(String)role.getSelectedItem();
        if(u.isEmpty()||p.isEmpty()) {
            msg("Enter username and password.");
            return;
        }
        String sql="SELECT NAME,ROLE,STATUS FROM USERS WHERE USERNAME=? AND PASSWORD=?";
        try(Connection con=DBConnection.getConnection();PreparedStatement ps=con.prepareStatement(sql)) {
            ps.setString(1, u);
            ps.setString(2, p);
            try(ResultSet rs=ps.executeQuery()) {
                if(!rs.next()) {
                    msg("Invalid username or password.");
                    return;
                }
                String actual=rs.getString("ROLE"), status=rs.getString("STATUS");
                boolean manager=actual.equalsIgnoreCase("ADMIN")||actual.equalsIgnoreCase("HOTEL MANAGER");
                boolean selectedManager=r.equals("ADMIN")||r.equals("HOTEL MANAGER");
                if(!(manager&&selectedManager)&&!actual.equalsIgnoreCase(r)) {
                    msg("Selected role does not match this account.");
                    return;
                }
                if(!"ACTIVE".equalsIgnoreCase(status)) {
                    msg(status!=null&&status.equalsIgnoreCase("PENDING")?"Your account is awaiting manager approval.":"This account is not active. Contact the manager.");
                    return;
                }
                Session.currentUser=new User(u, rs.getString("NAME"), actual);
                new Dashboard().setVisible(true);
                dispose();
            }
        }
                catch(Exception ex) {
            msg("Login error: "+ex.getMessage());
        }
    }
    private void msg(String s) {
        JOptionPane.showMessageDialog(this, s);
    }
}
