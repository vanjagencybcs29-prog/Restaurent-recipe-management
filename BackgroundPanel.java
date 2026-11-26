package recipemanagementpack;
import javax.swing.*;
import java.awt.*;
public class BackgroundPanel extends JPanel {
    private final Image image;
    public BackgroundPanel(String path) {
        ImageIcon icon=new ImageIcon(path);
        image=icon.getIconWidth()>0?icon.getImage():null;
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if(image==null)return;
        int iw=image.getWidth(this), ih=image.getHeight(this);
        if(iw<=0||ih<=0)return;
        double s=Math.max((double)getWidth()/iw, (double)getHeight()/ih);
        int w=(int)(iw*s), h=(int)(ih*s);
        Graphics2D g2=(Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(image, (getWidth()-w)/2, (getHeight()-h)/2, w, h, this);
        g2.setColor(new Color(0, 0, 0, 90));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }
}
