package recipemanagementpack;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;

/*
 * Dashboard.java  -  the main screen shown after login.
 * One class builds all three role screens:
 *     CUSTOMER      -> buildCustomer()
 *     CHEF          -> buildChef()
 *     HOTEL MANAGER -> buildManager()
 * Every table on screen is filled from an SQL query (see tablePanel and loadTable).
 *
 * WHERE TO CHANGE WHAT
 *     Tab names ............. buildCustomer / buildChef / buildManager
 *     Table headings ........ HEADINGS list (just above heading())
 *     Button colours, fonts . AppStyle.java
 *     Dish status words ..... loadMenu()   ("Available", "SOLD OUT", "Being prepared")
 *     "Only N left" limit ... loadMenu()   (remaining <= 3)
 *     Stock check rule ...... checkPlanStock()
 *     Prices and stock ...... done by the manager inside the app
 */
/** Role-based application workspace. Uses the user's existing Oracle schema. */
public class Dashboard extends JFrame {
    private final User user=Session.currentUser;
    private final JTabbedPane tabs=new JTabbedPane();
    public Dashboard() {
        if(user==null) {
            new LoginPage().setVisible(true);
            dispose();
            return;
        }
        setTitle("The Vintage Recipe Book | "+user.getRole());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setExtendedState(MAXIMIZED_BOTH);
        getContentPane().setBackground(AppStyle.BG);
        setLayout(new BorderLayout());
        JPanel top=new JPanel(new BorderLayout());
        top.setBackground(AppStyle.BG);
        top.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        JLabel welcome=new JLabel("THE VINTAGE RECIPE BOOK     |     Welcome, "+user.getName()+"  ("+user.getRole()+")");
        welcome.setForeground(AppStyle.GOLD);
        welcome.setFont(new Font("Serif", Font.BOLD, 19));
        top.add(welcome, BorderLayout.WEST);
        JButton logout=new JButton("Logout");
        AppStyle.styleButton(logout);
        logout.addActionListener(e-> {
            Session.currentUser=null;
            new recipemanagement().setVisible(true);
            dispose();
        });
        top.add(logout, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 14));
        add(tabs, BorderLayout.CENTER);
        if(isManager())buildManager();
        else if("CHEF".equalsIgnoreCase(user.getRole()))buildChef();
        else buildCustomer();
        addNotificationsTab();
    }
    private boolean isManager() {
        return "ADMIN".equalsIgnoreCase(user.getRole())||"HOTEL MANAGER".equalsIgnoreCase(user.getRole());
    }
    // Customer screen: Today's Menu, My Favourites, My Orders.
    private void buildCustomer() {
        tabs.addTab("Today's Menu", customerMenu());
        tabs.addTab("My Favourites", favouritesPanel());
        tabs.addTab("My Orders", ordersPanel());
    }
    // Chef screen: Recipes, Daily Cooking Plans, Ingredient Requests, Stock Status.
    private void buildChef() {
        tabs.addTab("Recipes", browseMenu("CHEF"));
        tabs.addTab("Daily Cooking Plans", chefPlans());
        tabs.addTab("Ingredient Requests", chefRequests());
        tabs.addTab("Stock Status", queryPanel("SELECT INGREDIENT_ID, NAME, UNIT, STOCK_QTY, LOW_LEVEL FROM INGREDIENTS ORDER BY NAME", null));
    }
    // Manager screen: Gallery, Chefs, Plan Approvals, Stock, Requests, Reports, Recipes.
    private void buildManager() {
        tabs.addTab("Recipe Gallery", browseMenu("MANAGER"));
        tabs.addTab("Chefs", chefApprovalPanel());
        tabs.addTab("Cooking Plan Approvals", planApprovalPanel());
        tabs.addTab("Stock Management", stockPanel());
        tabs.addTab("Ingredient Requests", requestApprovalPanel());
        tabs.addTab("Reports", reportsPanel());
        tabs.addTab("Recipes", recipesPanel());
    }

    // ---------- Customer menu: photo cards (dish info only, no ingredients or instructions) ----------
    /** Label that paints its photo to fill the whole area (cropped, no side bands). */
    private static final class CoverLabel extends JLabel {
        private Image img;
        CoverLabel(String t) {
            super(t, SwingConstants.CENTER);
            setOpaque(true);
            setBackground(new Color(225, 215, 195));
            setForeground(AppStyle.BROWN);
            setPreferredSize(new Dimension(10, 210));
        }
        void setPhoto(ImageIcon i) {
            img=i==null?null:i.getImage();
            if(img!=null)setText(null);
            repaint();
        }
        @Override protected void paintComponent(Graphics g) {
            if(img==null) {
                super.paintComponent(g);
                return;
            }
            int w=getWidth(), h=getHeight(), iw=img.getWidth(null), ih=img.getHeight(null);
            if(iw<=0||ih<=0||w<=0||h<=0) {
                super.paintComponent(g);
                return;
            }
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double sc=Math.max((double)w/iw, (double)h/ih);
            int dw=(int)(iw*sc), dh=(int)(ih*sc);
            g2.drawImage(img, (w-dw)/2, (h-dh)/2, dw, dh, null);
            g2.dispose();
        }
    }
    /** Panel that always matches the scroll pane's width, so there is never a sideways scrollbar. */
    private static final class WidthPanel extends JPanel implements Scrollable {
        WidthPanel(LayoutManager lm) {
            super(lm);
        }
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 24;
        }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return 120;
        }
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
    private static final class MenuItem {
        String cuisineName;
        Integer planId;
        int cuisineId;
        int recipeId;
        String name, desc, veg, availability="Not available today", imgText;
        double price;
        int remaining;
        byte[] imgBytes;
    }
    // Finds the image column of the RECIPES table (if there is one).
    private String imageColumn(Connection c)throws SQLException {
        try(Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT * FROM RECIPES WHERE 1=0")) {
            ResultSetMetaData md=r.getMetaData();
            for(int i=1;i<=md.getColumnCount();i++) {
                String n=md.getColumnName(i).toUpperCase();
                if(n.contains("IMAGE")||n.contains("PHOTO")||n.contains("PICTURE")||n.equals("IMG")||n.equals("PIC")||n.contains("IMG_"))return n;
            }
        }
        return null;
    }
    // Reads all dishes from the database and works out today's status of each dish.
    private java.util.List<MenuItem> loadMenu() {
        java.util.List<MenuItem> items=new ArrayList<>();
        java.util.Map<Integer, MenuItem> byRecipe=new HashMap<>();
        try(Connection c=DBConnection.getConnection()) {
            String img=imageColumn(c);
            boolean blob=false;
            if(img!=null) {
                try(Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT "+img+" FROM RECIPES WHERE 1=0")) {
                    int t=r.getMetaData().getColumnType(1);
                    blob=(t==Types.BLOB||t==Types.BINARY||t==Types.VARBINARY||t==Types.LONGVARBINARY);
                }
            }
            try(Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT RECIPE_ID,NAME,SHORT_DESCRIPTION,PRICE,IS_VEG,CUISINE_ID"+(img!=null?","+img:"")+" FROM RECIPES ORDER BY NAME")) {
                while(r.next()) {
                    MenuItem m=new MenuItem();
                    m.recipeId=r.getInt(1);
                    m.name=r.getString(2);
                    m.desc=r.getString(3);
                    m.price=r.getDouble(4);
                    m.veg=r.getString(5);
                    m.cuisineId=r.getInt(6);
                    if(img!=null) {
                        try {
                            if(blob)m.imgBytes=r.getBytes(7);
                            else m.imgText=r.getString(7);
                        } catch(Exception ignore){}
                    }
                    items.add(m);
                    byRecipe.put(m.recipeId, m);
                }
            }
            String plans="SELECT p.RECIPE_ID,p.PLAN_ID,NVL(p.COOKED,'N') COOKED,p.PORTIONS-NVL((SELECT SUM(o.QUANTITY) FROM ORDERS o WHERE o.PLAN_ID=p.PLAN_ID AND o.ORDER_STATUS='PLACED'),0) REMAINING FROM DAILY_PLANS p WHERE TRUNC(p.PLAN_DATE)=TRUNC(SYSDATE) AND p.STATUS='APPROVED' ORDER BY CASE NVL(p.COOKED,'N') WHEN 'Y' THEN 0 ELSE 1 END,REMAINING DESC";
            try(Statement st=c.createStatement();ResultSet r=st.executeQuery(plans)) {
                while(r.next()) {
                    MenuItem m=byRecipe.get(r.getInt("RECIPE_ID"));
                    if(m==null||m.planId!=null)continue;
                    m.planId=r.getInt("PLAN_ID");
                    m.remaining=r.getInt("REMAINING");
                    if(!"Y".equals(r.getString("COOKED")))m.availability="Being prepared";
                    else if(m.remaining<=0)m.availability="SOLD OUT";
                    else if(m.remaining<=3)m.availability="Only "+m.remaining+" left";
                    else m.availability="Available";
                }
            }
        } catch(Exception ex) {
            msg("Could not load menu: "+ex.getMessage());
        }
        return items;
    }
    // Finds the photo of one dish.
    private ImageIcon dishImage(MenuItem m) {
        try {
            if(m.imgBytes!=null&&m.imgBytes.length>0) {
                ImageIcon i=new ImageIcon(m.imgBytes);
                if(i.getIconWidth()>0)return i;
            }
            java.util.List<String> tries=new ArrayList<>();
            if(m.imgText!=null&&!m.imgText.isBlank()) {
                String t=m.imgText.trim();
                tries.add(t);
                tries.add("Images/"+t);
                tries.add("images/"+t);
            }
            for(String ext:new String[]{".jpg", ".jpeg", ".png", ".JPG", ".PNG"}) {
                tries.add("Images/"+m.name+ext);
                tries.add("Images/"+m.name.toLowerCase().replace(' ', '_')+ext);
                tries.add("Images/"+m.recipeId+ext);
            }
            for(String t:tries) {
                ImageIcon i;
                if(t.toLowerCase().startsWith("http"))i=new ImageIcon(new java.net.URL(t));
                else {
                    java.io.File f=new java.io.File(t);
                    if(!f.exists())continue;
                    i=new ImageIcon(f.getPath());
                }
                if(i.getIconWidth()>0)return i;
            }
        } catch(Exception ignore){}
                return null;
    }
    private final java.util.Map<String, ImageIcon> cuisineImgCache=new java.util.HashMap<>();
    /** Finds the cuisine photo, e.g. cuisine_images/thai.png (also checks Images/ and other spellings). */
    // Finds the photo of a cuisine from the cuisine_images folder.
    private ImageIcon cuisineImage(String cuisine) {
        if(cuisine==null)return null;
        String key=cuisine.toLowerCase();
        synchronized(cuisineImgCache) {
            if(cuisineImgCache.containsKey(key))return cuisineImgCache.get(key);
        }
        ImageIcon found=null;
        String[] dirs={"cuisine_images", "Images/cuisine_images", "Images", "images", "src/cuisine_images", ""};
        String[] names={cuisine, cuisine.toLowerCase(), cuisine.toLowerCase().replace(' ', '_'), cuisine.trim()};
        String[] exts={".png", ".jpg", ".jpeg"};
        outer:for(String d:dirs)for(String n:names)for(String e:exts) {
            java.io.File f=new java.io.File(d.isEmpty()?n+e:d+"/"+n+e);
            if(f.exists()) {
                ImageIcon i=new ImageIcon(f.getPath());
                if(i.getIconWidth()>0) {
                    found=i;
                    break outer;
                }
            }
        }
        if(found==null)found=searchImage(cuisine);
        if(found==null)System.err.println("Cuisine photo not found for '"+cuisine+"'. App working folder: "+System.getProperty("user.dir"));
        synchronized(cuisineImgCache) {
            cuisineImgCache.put(key, found);
        }
        return found;
    }
    /** Wider search: classpath (src/bin), project folders, parent folders, Downloads and Desktop. */
    // Last-resort search for a cuisine photo in common folders.
    private ImageIcon searchImage(String cuisine) {
        String want=cuisine.trim().toLowerCase().replace(' ', '_');
        String[] exts={".png", ".jpg", ".jpeg"};
        for(String d:new String[]{"/cuisine_images/", "/Images/", "/images/", "/"})for(String e:exts) {
            java.net.URL u=getClass().getResource(d+want+e);
            if(u==null)u=getClass().getResource(d+cuisine.trim()+e);
            if(u!=null) {
                ImageIcon i=new ImageIcon(u);
                if(i.getIconWidth()>0)return i;
            }
        }
        java.util.List<java.nio.file.Path> roots=new ArrayList<>();
        java.nio.file.Path base=java.nio.file.Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        roots.add(base);
        java.nio.file.Path par=base.getParent();
        for(int i=0;i<2&&par!=null;i++) {
            roots.add(par);
            par=par.getParent();
        }
        java.nio.file.Path home=java.nio.file.Paths.get(System.getProperty("user.home"));
        roots.add(home.resolve("Downloads"));
        roots.add(home.resolve("Desktop"));
        java.util.Set<String> names=new java.util.HashSet<>();
        for(String e:exts)names.add(want+e);
        for(java.nio.file.Path root:roots) {
            if(!java.nio.file.Files.isDirectory(root))continue;
            try(java.util.stream.Stream<java.nio.file.Path> st=java.nio.file.Files.walk(root, 5)) {
                java.util.Optional<java.nio.file.Path> hit=st.filter(x->x.getFileName()!=null&&names.contains(x.getFileName().toString().toLowerCase())&&java.nio.file.Files.isRegularFile(x)).findFirst();
                if(hit.isPresent()) {
                    ImageIcon i=new ImageIcon(hit.get().toString());
                    if(i.getIconWidth()>0)return i;
                }
            } catch(Exception ignore){}
        }
        return null;
    }
    // Scales a photo so it fills a box.
    private ImageIcon cover(ImageIcon src, int w, int h) {
        java.awt.image.BufferedImage out=new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g=out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double sc=Math.max((double)w/src.getIconWidth(), (double)h/src.getIconHeight());
        int dw=(int)(src.getIconWidth()*sc), dh=(int)(src.getIconHeight()*sc);
        g.drawImage(src.getImage(), (w-dw)/2, (h-dh)/2, dw, dh, null);
        g.dispose();
        return new ImageIcon(out);
    }
    // Builds ONE dish card: photo, name, price, status badge and buttons.
    private JPanel menuCard(MenuItem m, Runnable reload, String mode) {
        JPanel card=new JPanel(new BorderLayout(0, 6));
        card.setBackground(AppStyle.CREAM);
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AppStyle.GOLD, 2), BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        CoverLabel pic=new CoverLabel("Loading photo...");
        card.add(pic, BorderLayout.NORTH);
        new Thread(()-> {
            ImageIcon raw=dishImage(m);
            if(raw==null)raw=cuisineImage(m.cuisineName);
            final ImageIcon ic=raw;
            SwingUtilities.invokeLater(()-> {
                if(ic!=null)pic.setPhoto(ic);
                else pic.setText(m.name+" (no photo)");
            });
        }).start();
        JPanel info=new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);
        JLabel nm=new JLabel(m.name);
        nm.setFont(new Font("Serif", Font.BOLD, 19));
        nm.setForeground(AppStyle.BROWN);
        JLabel tag=new JLabel("\u20B9 "+String.format("%.2f", m.price));
        tag.setFont(new Font("Segoe UI", Font.BOLD, 14));
        tag.setForeground(AppStyle.BROWN);
        JLabel ds=new JLabel("<html><div style='width:270px'>"+(m.desc==null?"":m.desc.replace("<", "&lt;"))+"</div></html>");
        ds.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        ds.setForeground(AppStyle.BROWN);
        JLabel badge=new JLabel(" "+m.availability+" ");
        badge.setOpaque(true);
        badge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        badge.setForeground(Color.WHITE);
        boolean canOrder=m.planId!=null&&(m.availability.equals("Available")||m.availability.startsWith("Only"));
        badge.setBackground(m.availability.equals("Available")?new Color(30, 130, 50):m.availability.startsWith("Only")?new Color(210, 120, 20):m.availability.equals("SOLD OUT")?new Color(170, 30, 30):new Color(110, 110, 110));
        info.add(nm);
        info.add(Box.createVerticalStrut(3));
        info.add(tag);
        info.add(Box.createVerticalStrut(4));
        info.add(ds);
        info.add(Box.createVerticalStrut(6));
        info.add(badge);
        card.add(info, BorderLayout.CENTER);
        JPanel btns=new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btns.setOpaque(false);
        if("CUSTOMER".equals(mode)) {
            JButton order=new JButton("Order"), fav=new JButton("\u2665 Favourite");
            AppStyle.styleButton(order);
            AppStyle.styleButton(fav);
            order.setEnabled(canOrder);
            btns.add(order);
            btns.add(fav);
            order.addActionListener(e-> {
                String val=JOptionPane.showInputDialog(this, "How many portions of "+m.name+"?", "1");
                if(val==null)return;
                try {
                    int q=Integer.parseInt(val.trim());
                    if(q<1)throw new NumberFormatException();
                    placeOrder(m.planId, q);
                    reload.run();
                } catch(NumberFormatException ex) {
                    msg("Enter a valid positive quantity.");
                }
            });
            fav.addActionListener(e-> {
                try(Connection c=DBConnection.getConnection();PreparedStatement q=c.prepareStatement("INSERT INTO FAVOURITES(USERNAME,RECIPE_ID,ADDED_ON) VALUES(?,?,SYSDATE)")) {
                    q.setString(1, user.getUsername());
                    q.setInt(2, m.recipeId);
                    q.executeUpdate();
                    msg(m.name+" added to your favourites.");
                } catch(SQLException ex) {
                    msg(ex.getErrorCode()==1?"Already in your favourites.":"Could not add favourite: "+ex.getMessage());
                }
            });
        } else {
            JButton ing=new JButton("Ingredients"), price=new JButton("Set Price");
            AppStyle.styleButton(ing);
            AppStyle.styleButton(price);
            if("CHEF".equals(mode)) {
                JButton edit=new JButton("Edit Recipe");
                AppStyle.styleButton(edit);
                btns.add(edit);
                edit.addActionListener(e-> {
                    editRecipe(m.recipeId);
                    reload.run();
                });
                btns.add(ing);
            } else {
                btns.add(ing);
                btns.add(price);
            }
            ing.addActionListener(e->recipeIngredients(m.recipeId, m.name));
            price.addActionListener(e-> {
                String v=JOptionPane.showInputDialog(this, "New price for "+m.name+":", String.valueOf(m.price));
                if(v==null)return;
                try(Connection c=DBConnection.getConnection();PreparedStatement q=c.prepareStatement("UPDATE RECIPES SET PRICE=? WHERE RECIPE_ID=?")) {
                    double d=Double.parseDouble(v.trim());
                    if(d<0)throw new NumberFormatException();
                    q.setDouble(1, d);
                    q.setInt(2, m.recipeId);
                    q.executeUpdate();
                    logAction("PRICE_SET", m.name+" = "+d);
                    reload.run();
                } catch(NumberFormatException ex) {
                    msg("Enter a valid price.");
                } catch(Exception ex) {
                    msg("Could not update price: "+ex.getMessage());
                }
            });
            if("CHEF".equals(mode)) {
                JButton plan=new JButton("Cook Today");
                AppStyle.styleButton(plan);
                btns.add(plan);
                plan.addActionListener(e-> {
                    proposePlanFor(m.recipeId, m.name);
                    reload.run();
                });
            }
        }
        card.add(btns, BorderLayout.SOUTH);
        return card;
    }
    private java.util.Map<Integer, String> loadCuisineNames() {
        return loadLookup(new String[]{"CUISINES", "CUISINE"});
    }
    private java.util.Map<Integer, String> loadCategoryNames() {
        return loadLookup(new String[]{"CATEGORIES", "CATEGORY"});
    }
    private java.util.Map<Integer, String> loadLookup(String[] tables) {
        java.util.Map<Integer, String> out=new java.util.LinkedHashMap<>();
        try(Connection c=DBConnection.getConnection()) {
            for(String t:tables) {
                String idc=null, nmc=null;
                try(Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT * FROM "+t+" WHERE 1=0")) {
                    ResultSetMetaData md=r.getMetaData();
                    for(int i=1;i<=md.getColumnCount();i++) {
                        String n=md.getColumnName(i).toUpperCase();
                        if(idc==null&&n.contains("ID"))idc=n;
                        else if(nmc==null&&n.contains("NAME"))nmc=n;
                    }
                    if(nmc==null&&md.getColumnCount()>=2)nmc=md.getColumnName(2);
                } catch(SQLException e) {
                    continue;
                }
                if(idc==null||nmc==null)continue;
                try(Statement st=c.createStatement();ResultSet r=st.executeQuery("SELECT "+idc+","+nmc+" FROM "+t+" ORDER BY "+nmc)) {
                    while(r.next())out.put(r.getInt(1), r.getString(2));
                } catch(SQLException e) {
                    continue;
                }
                if(!out.isEmpty())break;
            }
        } catch(Exception ignore){}
                return out;
    }
    private JPanel customerMenu() {
        return browseMenu("CUSTOMER");
    }
    // Cuisine cards -> click one -> dish cards. Used by customer, chef and manager.
    private JPanel browseMenu(String mode) {
        JPanel root=new JPanel(new BorderLayout(8, 8));
        root.setBackground(AppStyle.BG);
        JLabel title=AppStyle.heading("OUR CUISINES");
        // Search bar: type a dish, cuisine or ingredient word to see matching dishes.
        JTextField search=new JTextField(28);
        search.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        JLabel searchLbl=new JLabel("Search dishes:");
        searchLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        searchLbl.setForeground(AppStyle.CREAM);
        JPanel searchBar=new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        searchBar.setBackground(AppStyle.BG);
        searchBar.add(searchLbl);
        searchBar.add(search);
        JPanel north=new JPanel(new BorderLayout());
        north.setBackground(AppStyle.BG);
        north.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        north.add(title, BorderLayout.NORTH);
        north.add(searchBar, BorderLayout.SOUTH);
        root.add(north, BorderLayout.NORTH);
        CardLayout cl=new CardLayout();
        JPanel deck=new JPanel(cl);
        deck.setBackground(AppStyle.BG);
        JPanel cuisineGrid=new JPanel(new GridLayout(0, 4, 16, 16));
        cuisineGrid.setBackground(AppStyle.BG);
        cuisineGrid.setBorder(BorderFactory.createEmptyBorder(8, 16, 16, 16));
        JPanel cHolder=new WidthPanel(new BorderLayout());
        cHolder.setBackground(AppStyle.BG);
        cHolder.add(cuisineGrid, BorderLayout.NORTH);
        JScrollPane cSp=new JScrollPane(cHolder);
        cSp.getVerticalScrollBar().setUnitIncrement(24);
        cSp.getViewport().setBackground(AppStyle.BG);
        JPanel dishGrid=new JPanel(new GridLayout(0, 4, 16, 16));
        dishGrid.setBackground(AppStyle.BG);
        dishGrid.setBorder(BorderFactory.createEmptyBorder(8, 16, 16, 16));
        JPanel dHolder=new WidthPanel(new BorderLayout());
        dHolder.setBackground(AppStyle.BG);
        dHolder.add(dishGrid, BorderLayout.NORTH);
        JScrollPane dSp=new JScrollPane(dHolder);
        dSp.getVerticalScrollBar().setUnitIncrement(24);
        dSp.getViewport().setBackground(AppStyle.BG);
        JButton back=new JButton("\u2190 Back to Cuisines");
        AppStyle.styleButton(back);
        JPanel dishView=new JPanel(new BorderLayout(6, 6));
        dishView.setBackground(AppStyle.BG);
        JPanel backBar=new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        backBar.setBackground(AppStyle.BG);
        backBar.add(back);
        dishView.add(backBar, BorderLayout.NORTH);
        dishView.add(dSp, BorderLayout.CENTER);
        deck.add(cSp, "cuisines");
        deck.add(dishView, "dishes");
        root.add(deck, BorderLayout.CENTER);
        int[] curId={-1};
        String[] curName={""};
        Runnable[] showDishes=new Runnable[1], showCuisines=new Runnable[1];
        showDishes[0]=()-> {
            dishGrid.removeAll();
            int n=0;
            for(MenuItem m:loadMenu())if(m.cuisineId==curId[0]) {
                m.cuisineName=curName[0];
                dishGrid.add(menuCard(m, showDishes[0], mode));
                n++;
            }
            if(n==0)dishGrid.add(new JLabel("No dishes in this cuisine yet."));
            title.setText(curName[0].toUpperCase());
            dishGrid.revalidate();
            dishGrid.repaint();
            cl.show(deck, "dishes");
        };
        showCuisines[0]=()-> {
            cuisineGrid.removeAll();
            title.setText("OUR CUISINES");
            java.util.List<MenuItem> items=loadMenu();
            java.util.Map<Integer, String> names=loadCuisineNames();
            java.util.Map<Integer, java.util.List<MenuItem>> by=new java.util.LinkedHashMap<>();
            for(MenuItem m:items)by.computeIfAbsent(m.cuisineId, k->new ArrayList<>()).add(m);
            for(Integer cid:names.keySet())by.computeIfAbsent(cid, k->new ArrayList<>());
            java.util.List<Integer> ids=new ArrayList<>(by.keySet());
            ids.sort((a, b)->names.getOrDefault(a, "Cuisine "+a).compareToIgnoreCase(names.getOrDefault(b, "Cuisine "+b)));
            for(Integer id:ids) {
                String nm=names.getOrDefault(id, "Cuisine "+id);
                java.util.List<MenuItem> dishes=by.get(id);
                JPanel card=new JPanel(new BorderLayout(0, 6));
                card.setBackground(AppStyle.CREAM);
                card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AppStyle.GOLD, 2), BorderFactory.createEmptyBorder(8, 8, 8, 8)));
                card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                CoverLabel pic=new CoverLabel("");
                card.add(pic, BorderLayout.NORTH);
                new Thread(()-> {
                    ImageIcon raw=cuisineImage(nm);
                    for(int i=0;raw==null&&i<dishes.size();i++)raw=dishImage(dishes.get(i));
                    final ImageIcon ic=raw;
                    SwingUtilities.invokeLater(()-> {
                        if(ic!=null)pic.setPhoto(ic);
                        else pic.setText(nm+" (photo not found)");
                    });
                }).start();
                JLabel nl=new JLabel(nm, SwingConstants.CENTER);
                nl.setFont(new Font("Serif", Font.BOLD, 22));
                nl.setForeground(AppStyle.BROWN);
                JLabel cnt=new JLabel(dishes.size()+" dish"+(dishes.size()==1?"":"es"), SwingConstants.CENTER);
                cnt.setForeground(AppStyle.BROWN);
                JPanel mid=new JPanel(new GridLayout(2, 1));
                mid.setOpaque(false);
                mid.add(nl);
                mid.add(cnt);
                card.add(mid, BorderLayout.CENTER);
                JButton view=new JButton("View Dishes");
                AppStyle.styleButton(view);
                card.add(view, BorderLayout.SOUTH);
                Runnable open=()-> {
                    curId[0]=id;
                    curName[0]=nm;
                    showDishes[0].run();
                };
                view.addActionListener(e->open.run());
                card.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                        open.run();
                    }
                });
                pic.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                        open.run();
                    }
                });
                cuisineGrid.add(card);
            }
            if(ids.isEmpty())cuisineGrid.add(new JLabel("No dishes yet."));
            cuisineGrid.revalidate();
            cuisineGrid.repaint();
            cl.show(deck, "cuisines");
        };
        // Shows dishes (from every cuisine) whose name, description or cuisine matches the search text.
        Runnable[] showSearch=new Runnable[1];
        showSearch[0]=()-> {
            String q=search.getText().trim().toLowerCase();
            if(q.isEmpty()) {
                showCuisines[0].run();
                return;
            }
            dishGrid.removeAll();
            java.util.Map<Integer, String> cuisineNames=loadCuisineNames();
            int found=0;
            for(MenuItem m:loadMenu()) {
                String cn=cuisineNames.getOrDefault(m.cuisineId, "");
                String hay=(nz(m.name)+" "+nz(m.desc)+" "+cn).toLowerCase();
                if(hay.contains(q)) {
                    m.cuisineName=cn;
                    dishGrid.add(menuCard(m, showSearch[0], mode));
                    found++;
                }
            }
            if(found==0) {
                JLabel none=new JLabel("No dishes match \""+search.getText().trim()+"\".");
                none.setForeground(AppStyle.CREAM);
                none.setFont(new Font("Segoe UI", Font.ITALIC, 16));
                dishGrid.add(none);
            }
            title.setText("SEARCH RESULTS");
            dishGrid.revalidate();
            dishGrid.repaint();
            cl.show(deck, "dishes");
        };
        // Wait a moment after the last key press, so the screen does not reload on every letter.
        javax.swing.Timer searchDelay=new javax.swing.Timer(350, e->showSearch[0].run());
        searchDelay.setRepeats(false);
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                searchDelay.restart();
            }
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                searchDelay.restart();
            }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                searchDelay.restart();
            }
        });
        back.addActionListener(e-> {
            search.setText("");
            showCuisines[0].run();
        });
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.CENTER));
        actions.setBackground(AppStyle.BG);
        JButton refresh=new JButton("Refresh");
        AppStyle.styleButton(refresh);
        if("CHEF".equals(mode)) {
            JButton addR=new JButton("+ Add New Recipe");
            AppStyle.styleButton(addR);
            actions.add(addR);
            addR.addActionListener(e-> {
                addRecipe();
                showCuisines[0].run();
            });
        }
        actions.add(refresh);
        root.add(actions, BorderLayout.SOUTH);
        refresh.addActionListener(e-> {
            if(!search.getText().trim().isEmpty())showSearch[0].run();
            else if(curId[0]>=0&&dishView.isShowing())showDishes[0].run();
            else showCuisines[0].run();
        });
        showCuisines[0].run();
        return root;
    }
    // Customer orders portions. The plan row is locked so two customers cannot take the last portion.
    private void placeOrder(int planId, int qty) {
        String lock="SELECT PORTIONS,STATUS,PLAN_DATE,COOKED FROM DAILY_PLANS WHERE PLAN_ID=? FOR UPDATE";
        try(Connection c=DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try(PreparedStatement p=c.prepareStatement(lock)) {
                p.setInt(1, planId);
                try(ResultSet r=p.executeQuery()) {
                    if(!r.next())throw new SQLException("This menu item is no longer available.");
                    if(!"APPROVED".equalsIgnoreCase(r.getString("STATUS")))throw new SQLException("This dish is not approved today.");
                    if(r.getDate("PLAN_DATE")==null||!r.getDate("PLAN_DATE").toLocalDate().equals(LocalDate.now()))throw new SQLException("This dish is not on today's menu.");
                    if(!"Y".equalsIgnoreCase(r.getString("COOKED")))throw new SQLException("This dish is still being prepared.");
                    int total=r.getInt("PORTIONS"), used=0;
                    try(PreparedStatement q=c.prepareStatement("SELECT NVL(SUM(QUANTITY),0) FROM ORDERS WHERE PLAN_ID=? AND ORDER_STATUS='PLACED'")) {
                        q.setInt(1, planId);
                        try(ResultSet s=q.executeQuery()) {
                            if(s.next())used=s.getInt(1);
                        }
                    }
                    int left=total-used;
                    if(qty>left)throw new SQLException(left==0?"Sorry, this dish has sold out.":"Only "+left+" portion(s) remain.");
                    try(PreparedStatement ins=c.prepareStatement("INSERT INTO ORDERS(ORDER_ID,PLAN_ID,CUSTOMER_USERNAME,QUANTITY,ORDER_STATUS,CREATED_ON) VALUES(SEQ_ORDERS.NEXTVAL,?,?,?,'PLACED',SYSDATE)")) {
                        ins.setInt(1, planId);
                        ins.setString(2, user.getUsername());
                        ins.setInt(3, qty);
                        ins.executeUpdate();
                    }
                    c.commit();
                    logAction("ORDER", "Plan "+planId+" x"+qty);
                    msg("Order placed successfully! Portions: "+qty);
                }
            } catch(Exception ex) {
                c.rollback();
                throw ex;
            }
        }
                catch(Exception ex) {
            msg("Order not placed: "+ex.getMessage());
        }
    }
    // Customer: My Favourites tab (add and remove).
    private JPanel favouritesPanel() {
        String sql="SELECT r.RECIPE_ID,r.NAME,r.SHORT_DESCRIPTION,r.PRICE FROM FAVOURITES f JOIN RECIPES r ON r.RECIPE_ID=f.RECIPE_ID WHERE f.USERNAME=? ORDER BY f.ADDED_ON DESC";
        JPanel p=tablePanel(sql, new Object[]{user.getUsername()});
        JTable t=(JTable)p.getClientProperty("table");
        JPanel b=buttonBar(p);
        JButton add=new JButton("Add Favourite"), remove=new JButton("Remove Selected Favourite");
        AppStyle.styleButton(add);
        AppStyle.styleButton(remove);
        b.add(add);
        b.add(remove);
        add.addActionListener(e-> {
            Object[] pk=pickFromList("Choose a dish to add to your favourites", "SELECT RECIPE_ID,NAME FROM RECIPES ORDER BY NAME");
            if(pk==null)return;
            try(Connection c=DBConnection.getConnection();PreparedStatement q=c.prepareStatement("INSERT INTO FAVOURITES(USERNAME,RECIPE_ID,ADDED_ON) VALUES(?,?,SYSDATE)")) {
                q.setString(1, user.getUsername());
                q.setInt(2, (Integer)pk[0]);
                q.executeUpdate();
                refreshTable(p, sql, new Object[]{user.getUsername()});
            } catch(SQLException ex) {
                msg(ex.getErrorCode()==1?"Already in your favourites.":"Could not add favourite: "+ex.getMessage());
            }
        });
        remove.addActionListener(e-> {
            int row=t.getSelectedRow();
            if(row<0) {
                msg("Select a favourite first.");
                return;
            }
            int id=Integer.parseInt(t.getValueAt(row, 0).toString());
            try(Connection c=DBConnection.getConnection();PreparedStatement q=c.prepareStatement("DELETE FROM FAVOURITES WHERE USERNAME=? AND RECIPE_ID=?")) {
                q.setString(1, user.getUsername());
                q.setInt(2, id);
                q.executeUpdate();
                refreshTable(p, sql, new Object[]{user.getUsername()});
            } catch(Exception ex) {
                msg("Could not remove favourite: "+ex.getMessage());
            }
        });
        return p;
    }
    // Customer: My Orders tab.
    private JPanel ordersPanel() {
        return queryPanel("SELECT o.ORDER_ID,r.NAME,o.QUANTITY,o.ORDER_STATUS,o.CREATED_ON,p.PLAN_DATE FROM ORDERS o JOIN DAILY_PLANS p ON p.PLAN_ID=o.PLAN_ID JOIN RECIPES r ON r.RECIPE_ID=p.RECIPE_ID WHERE o.CUSTOMER_USERNAME=? ORDER BY o.CREATED_ON DESC", new Object[]{user.getUsername()});
    }

    private JPanel chefRecipes() {
        String sql="SELECT RECIPE_ID,NAME,PRICE,AVAILABLE,TIME_MINUTES,IS_VEG,SERVINGS FROM RECIPES WHERE CREATED_BY=? ORDER BY NAME";
        JPanel p=tablePanel(sql, new Object[]{user.getUsername()});
        JTable t=(JTable)p.getClientProperty("table");
        JPanel buttons=buttonBar(p);
        JButton add=new JButton("Add Recipe"), ing=new JButton("Set Ingredients for Selected Recipe");
        AppStyle.styleButton(add);
        AppStyle.styleButton(ing);
        buttons.add(add);
        buttons.add(ing);
        add.addActionListener(e-> {
            addRecipe();
            refreshTable(p, sql, new Object[]{user.getUsername()});
        });
        ing.addActionListener(e-> {
            int row=t.getSelectedRow();
            if(row<0) {
                msg("Select a recipe first.");
                return;
            }
            recipeIngredients(Integer.parseInt(t.getValueAt(row, 0).toString()), String.valueOf(t.getValueAt(row, 1)));
        });
        return p;
    }
    private JComboBox<String> lookupCombo(java.util.Map<Integer, String> m) {
        JComboBox<String> b=new JComboBox<>();
        for(java.util.Map.Entry<Integer, String> e:m.entrySet())b.addItem(e.getKey()+" - "+e.getValue());
        if(m.isEmpty())b.setEditable(true);
        return b;
    }
    private JComboBox<String> cuisineCombo() {
        return lookupCombo(loadCuisineNames());
    }
    private void selectCombo(JComboBox<String> b, int id) {
        for(int i=0;i<b.getItemCount();i++)if(b.getItemAt(i).startsWith(id+" - ")) {
            b.setSelectedIndex(i);
            return;
        }
        if(b.isEditable())b.setSelectedItem(String.valueOf(id));
    }
    private Integer comboId(JComboBox<String> b) {
        Object o=b.getSelectedItem();
        if(o==null||o.toString().trim().isEmpty())return null;
        return Integer.parseInt(o.toString().split(" - ")[0].trim());
    }
    private static String nz(Object o) {
        return o==null?"":o.toString();
    }
    /** Tidy label/field form: every field keeps its natural height. */
    private JPanel labeledForm(Object... pairs) {
        JPanel f=new JPanel(new GridBagLayout());
        GridBagConstraints g=new GridBagConstraints();
        g.insets=new Insets(5, 6, 5, 6);
        g.anchor=GridBagConstraints.NORTHWEST;
        for(int i=0;i+1<pairs.length;i+=2) {
            g.gridy=i/2;
            g.gridx=0;
            g.weightx=0;
            g.fill=GridBagConstraints.NONE;
            JLabel l=new JLabel(pairs[i].toString());
            l.setFont(new Font("Segoe UI", Font.BOLD, 13));
            f.add(l, g);
            g.gridx=1;
            g.weightx=1;
            g.fill=GridBagConstraints.HORIZONTAL;
            f.add((JComponent)pairs[i+1], g);
        }
        return f;
    }
    /** Wraps a form so it scrolls if the screen is small. */
    private JComponent formScroll(JPanel form) {
        JScrollPane sp=new JScrollPane(form);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        int h=Math.min(520, Toolkit.getDefaultToolkit().getScreenSize().height-260);
        sp.setPreferredSize(new Dimension(640, h));
        return sp;
    }
    // Chef edits a recipe.
    private void editRecipe(int recipeId) {
        JTextField name=new JTextField(), minutes=new JTextField(), price=new JTextField(), servings=new JTextField(), desc=new JTextField();
        JTextArea ins=new JTextArea(7, 32);
        ins.setLineWrap(true);
        ins.setWrapStyleWord(true);
        JComboBox<String> veg=new JComboBox<>(new String[]{"Y", "N"}), cuisine=cuisineCombo(), category=lookupCombo(loadCategoryNames());
        try(Connection c=DBConnection.getConnection();PreparedStatement q=c.prepareStatement("SELECT NAME,CUISINE_ID,CATEGORY_ID,TIME_MINUTES,PRICE,SERVINGS,IS_VEG,SHORT_DESCRIPTION,INSTRUCTIONS FROM RECIPES WHERE RECIPE_ID=?")) {
            q.setInt(1, recipeId);
            try(ResultSet r=q.executeQuery()) {
                if(!r.next()) {
                    msg("Recipe not found.");
                    return;
                }
                name.setText(nz(r.getString(1)));
                if(r.getObject(2)!=null)selectCombo(cuisine, r.getInt(2));
                if(r.getObject(3)!=null)selectCombo(category, r.getInt(3));
                minutes.setText(nz(r.getObject(4)));
                price.setText(nz(r.getObject(5)));
                servings.setText(nz(r.getObject(6)));
                veg.setSelectedItem(nz(r.getString(7)).trim().isEmpty()?"Y":r.getString(7).trim());
                desc.setText(nz(r.getString(8)));
                ins.setText(nz(r.getString(9)));
            }
        } catch(Exception ex) {
            msg("Could not load recipe: "+ex.getMessage());
            return;
        }
        JPanel form=labeledForm("Recipe name", name, "Cuisine", cuisine, "Category", category, "Cooking time (minutes)", minutes, "Price", price, "Servings", servings, "Vegetarian? (Y/N)", veg, "Short description (shown to customers)", desc, "Cooking steps (chef only)", new JScrollPane(ins));
        if(JOptionPane.showConfirmDialog(this, formScroll(form), "Edit Recipe", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
        try(Connection c=DBConnection.getConnection();PreparedStatement u=c.prepareStatement("UPDATE RECIPES SET NAME=?,CUISINE_ID=?,CATEGORY_ID=?,TIME_MINUTES=?,PRICE=?,SERVINGS=?,IS_VEG=?,SHORT_DESCRIPTION=?,INSTRUCTIONS=? WHERE RECIPE_ID=?")) {
            if(name.getText().trim().isEmpty())throw new IllegalArgumentException("Recipe name is required.");
            u.setString(1, name.getText().trim());
            Integer cu=comboId(cuisine), ca=comboId(category);
            if(cu==null)u.setNull(2, Types.NUMERIC);
            else u.setInt(2, cu);
            if(ca==null)u.setNull(3, Types.NUMERIC);
            else u.setInt(3, ca);
            u.setInt(4, Integer.parseInt(minutes.getText().trim()));
            u.setDouble(5, Double.parseDouble(price.getText().trim()));
            u.setInt(6, Integer.parseInt(servings.getText().trim()));
            u.setString(7, veg.getSelectedItem().toString());
            u.setString(8, desc.getText());
            u.setString(9, ins.getText());
            u.setInt(10, recipeId);
            u.executeUpdate();
            logAction("RECIPE_EDITED", name.getText().trim());
            msg("Recipe updated.");
        } catch(NumberFormatException ex) {
            msg("Check the numbers: time, price and servings must be valid numbers.");
        } catch(Exception ex) {
            msg("Could not save recipe: "+ex.getMessage());
        }
    }
    // Chef adds a new recipe.
    private void addRecipe() {
        JComboBox<String> cuisine=cuisineCombo(), category=lookupCombo(loadCategoryNames());
        JTextField name=new JTextField(), minutes=new JTextField("30"), price=new JTextField("0"), servings=new JTextField("2"), description=new JTextField();
        JTextArea instructions=new JTextArea(5, 25);
        JComboBox<String> veg=new JComboBox<>(new String[]{"Y", "N"});
        instructions.setLineWrap(true);
        instructions.setWrapStyleWord(true);
        JPanel form=labeledForm("Recipe name", name, "Cuisine", cuisine, "Category", category, "Cooking time (minutes)", minutes, "Price", price, "Servings", servings, "Vegetarian? (Y/N)", veg, "Short description", description, "Instructions", new JScrollPane(instructions));
        if(JOptionPane.showConfirmDialog(this, formScroll(form), "Add Recipe", JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO RECIPES(RECIPE_ID,NAME,CUISINE_ID,CATEGORY_ID,IS_TRADITIONAL,INSTRUCTIONS,AVAILABLE,TIME_MINUTES,PRICE,IS_VEG,SERVINGS,SHORT_DESCRIPTION,CREATED_BY) VALUES((SELECT NVL(MAX(RECIPE_ID),0)+1 FROM RECIPES),?,?,?,?,?,1,?,?,?,?,?,?)")) {
            p.setString(1, name.getText().trim());
            Integer cuId=comboId(cuisine), caId=comboId(category);
            if(cuId==null)p.setNull(2, Types.NUMERIC);
            else p.setInt(2, cuId);
            if(caId==null)p.setNull(3, Types.NUMERIC);
            else p.setInt(3, caId);
            p.setInt(4, 0);
            p.setString(5, instructions.getText());
            p.setInt(6, Integer.parseInt(minutes.getText().trim()));
            p.setDouble(7, Double.parseDouble(price.getText().trim()));
            p.setString(8, veg.getSelectedItem().toString());
            p.setInt(9, Integer.parseInt(servings.getText().trim()));
            p.setString(10, description.getText());
            p.setString(11, user.getUsername());
            p.executeUpdate();
            logAction("RECIPE_ADDED", name.getText().trim());
            msg("Recipe added.");
        } catch(Exception ex) {
            msg("Could not add recipe: "+ex.getMessage());
        }
    }

    // Shows the ingredients of one recipe.
    private void recipeIngredients(int recipeId, String recipeName) {
        String listSql="SELECT i.INGREDIENT_ID,i.NAME,i.UNIT,ri.QUANTITY FROM RECIPE_INGREDIENTS ri JOIN INGREDIENTS i ON i.INGREDIENT_ID=ri.INGREDIENT_ID WHERE ri.RECIPE_ID=? ORDER BY i.NAME";
        JPanel list=tablePanel(listSql, new Object[]{recipeId});
        JTable lt=(JTable)list.getClientProperty("table");
        list.setPreferredSize(new Dimension(620, 320));
        JButton add=new JButton("Add / Update Ingredient"), del=new JButton("Remove Selected");
        AppStyle.styleButton(add);
        AppStyle.styleButton(del);
        JPanel b=buttonBar(list);
        b.add(add);
        b.add(del);
        add.addActionListener(e-> {
            JComboBox<String> box=new JComboBox<>();
            try(Connection c=DBConnection.getConnection();PreparedStatement q=c.prepareStatement("SELECT INGREDIENT_ID,NAME,UNIT FROM INGREDIENTS ORDER BY NAME");ResultSet r=q.executeQuery()) {
                while(r.next())box.addItem(r.getInt(1)+" - "+r.getString(2)+" ("+r.getString(3)+")");
            } catch(Exception ex) {
                msg("Could not load ingredients: "+ex.getMessage());
                return;
            }
            JTextField qty=new JTextField("1");
            JPanel f=new JPanel(new GridLayout(2, 2, 8, 8));
            addField(f, "Ingredient", box);
            addField(f, "Quantity per portion", qty);
            if(JOptionPane.showConfirmDialog(this, f, "Recipe Ingredient", JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION||box.getSelectedItem()==null)return;
            try(Connection c=DBConnection.getConnection()) {
                int iid=Integer.parseInt(box.getSelectedItem().toString().split(" - ")[0]);
                double n=Double.parseDouble(qty.getText().trim());
                if(n<=0)throw new NumberFormatException();
                try(PreparedStatement u=c.prepareStatement("UPDATE RECIPE_INGREDIENTS SET QUANTITY=? WHERE RECIPE_ID=? AND INGREDIENT_ID=?")) {
                    u.setDouble(1, n);
                    u.setInt(2, recipeId);
                    u.setInt(3, iid);
                    if(u.executeUpdate()==0)try(PreparedStatement ins=c.prepareStatement("INSERT INTO RECIPE_INGREDIENTS(RECIPE_ID,INGREDIENT_ID,QUANTITY) VALUES(?,?,?)")) {
                        ins.setInt(1, recipeId);
                        ins.setInt(2, iid);
                        ins.setDouble(3, n);
                        ins.executeUpdate();
                    }
                }
                refreshTable(list, listSql, new Object[]{recipeId});
            } catch(NumberFormatException ex) {
                msg("Enter a valid positive quantity.");
            } catch(Exception ex) {
                msg("Could not save ingredient: "+ex.getMessage());
            }
        });
        del.addActionListener(e-> {
            int row=lt.getSelectedRow();
            if(row<0) {
                msg("Select an ingredient row.");
                return;
            }
            int iid=Integer.parseInt(lt.getValueAt(row, 0).toString());
            try(Connection c=DBConnection.getConnection();PreparedStatement q=c.prepareStatement("DELETE FROM RECIPE_INGREDIENTS WHERE RECIPE_ID=? AND INGREDIENT_ID=?")) {
                q.setInt(1, recipeId);
                q.setInt(2, iid);
                q.executeUpdate();
                refreshTable(list, listSql, new Object[]{recipeId});
            } catch(Exception ex) {
                msg("Could not remove: "+ex.getMessage());
            }
        });
        JOptionPane.showMessageDialog(this, list, "Ingredients for "+recipeName, JOptionPane.PLAIN_MESSAGE);
    }
    private static final String CHEF_PLAN_SQL="SELECT p.PLAN_ID,p.RECIPE_ID,r.NAME RECIPE_NAME,p.PLAN_DATE,p.PORTIONS,p.STATUS,p.COOKED FROM DAILY_PLANS p JOIN RECIPES r ON r.RECIPE_ID=p.RECIPE_ID WHERE p.CHEF_USERNAME=? ORDER BY p.PLAN_DATE DESC";
    // Chef: Daily Cooking Plans tab.
    private JPanel chefPlans() {
        JPanel p=tablePanel(CHEF_PLAN_SQL, new Object[]{user.getUsername()});
        JTable t=(JTable)p.getClientProperty("table");
        JPanel b=buttonBar(p);
        JButton add=new JButton("Propose Today's Dish"), cooked=new JButton("Mark Selected Plan Cooked");
        AppStyle.styleButton(add);
        AppStyle.styleButton(cooked);
        JButton rf=new JButton("Refresh");
        AppStyle.styleButton(rf);
        b.add(add);
        b.add(cooked);
        b.add(rf);
        rf.addActionListener(e->refreshTable(p, CHEF_PLAN_SQL, new Object[]{user.getUsername()}));
        add.addActionListener(e-> {
            proposePlan();
            refreshTable(p, CHEF_PLAN_SQL, new Object[]{user.getUsername()});
        });
        cooked.addActionListener(e-> {
            int row=t.getSelectedRow();
            if(row<0) {
                msg("Select a plan.");
                return;
            }
            int id=Integer.parseInt(t.getValueAt(row, 0).toString());
            markCooked(id);
            refreshTable(p, CHEF_PLAN_SQL, new Object[]{user.getUsername()});
        });
        return p;
    }
    /** Shows a searchable list so users pick by NAME, never by typing an ID. Query must return (id, label). Returns {Integer id, String label} or null. */
    // Pop-up list with a search box. The user picks by NAME, never by typing an ID.
    private Object[] pickFromList(String title, String sql) {
        java.util.List<Integer> ids=new ArrayList<>();
        java.util.List<String> labels=new ArrayList<>();
        try(Connection c=DBConnection.getConnection();Statement st=c.createStatement();ResultSet r=st.executeQuery(sql)) {
            while(r.next()) {
                ids.add(r.getInt(1));
                labels.add(r.getString(2));
            }
        }
                catch(Exception ex) {
            msg("Could not load the list: "+ex.getMessage());
            return null;
        }
        if(ids.isEmpty()) {
            msg("There is nothing to choose from yet.");
            return null;
        }
        DefaultListModel<Integer> model=new DefaultListModel<>();
        JList<Integer> list=new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                return super.getListCellRendererComponent(l, labels.get((Integer)v), i, sel, foc);
            }
        });
        JTextField search=new JTextField();
        Runnable fill=()-> {
            String q=search.getText().trim().toLowerCase();
            model.clear();
            for(int i=0;i<labels.size();i++)if(q.isEmpty()||labels.get(i).toLowerCase().contains(q))model.addElement(i);
            if(!model.isEmpty())list.setSelectedIndex(0);
        };
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                fill.run();
            }
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                fill.run();
            }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                fill.run();
            }
        });
        fill.run();
        JPanel top=new JPanel(new BorderLayout(6, 6));
        top.add(new JLabel("Type to search:"), BorderLayout.WEST);
        top.add(search, BorderLayout.CENTER);
        JScrollPane sp=new JScrollPane(list);
        sp.setPreferredSize(new Dimension(420, 280));
        JPanel body=new JPanel(new BorderLayout(6, 8));
        body.add(top, BorderLayout.NORTH);
        body.add(sp, BorderLayout.CENTER);
        if(JOptionPane.showConfirmDialog(this, body, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return null;
        Integer idx=list.getSelectedValue();
        if(idx==null) {
            msg("Please select one item from the list.");
            return null;
        }
        return new Object[]{ids.get(idx), labels.get(idx)};
    }
    // Chef sends a plan (dish + portions) to the manager. Status starts as PENDING.
    private void proposePlanFor(int recipeId, String name) {
        JTextField portions=new JTextField("20");
        JPanel f=new JPanel(new GridLayout(1, 2, 8, 8));
        addField(f, "Portions of "+name, portions);
        if(JOptionPane.showConfirmDialog(this, f, "Propose to Cook Today", JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO DAILY_PLANS(PLAN_ID,CHEF_USERNAME,RECIPE_ID,PLAN_DATE,PORTIONS,STATUS,COOKED,CREATED_ON) VALUES(SEQ_PLANS.NEXTVAL,?,?,TRUNC(SYSDATE),?,'PENDING','N',SYSDATE)")) {
            int n=Integer.parseInt(portions.getText().trim());
            if(n<1)throw new NumberFormatException();
            p.setString(1, user.getUsername());
            p.setInt(2, recipeId);
            p.setInt(3, n);
            p.executeUpdate();
            notifyManagers("PLAN", "Chef "+user.getName()+" proposed "+n+" portions of "+name+" for today. Please review.");
            logAction("PLAN_PROPOSED", name+" x"+n);
            msg("Plan for "+name+" sent to the manager for approval.");
        } catch(NumberFormatException ex) {
            msg("Enter a valid number of portions.");
        } catch(Exception ex) {
            msg("Could not submit plan: "+ex.getMessage());
        }
    }
    // Chef picks a dish from the list, then proposes it.
    private void proposePlan() {
        Object[] pk=pickFromList("Choose a dish to cook today", "SELECT RECIPE_ID,NAME FROM RECIPES ORDER BY NAME");
        if(pk==null)return;
        proposePlanFor((Integer)pk[0], (String)pk[1]);
    }
    // Chef marks the plan cooked: ingredients are deducted from stock and the dish becomes orderable.
    private void markCooked(int id) {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                int recipeId, portions;
                try (PreparedStatement q = c.prepareStatement(
                        "SELECT RECIPE_ID,PORTIONS,STATUS,COOKED FROM DAILY_PLANS WHERE PLAN_ID=? AND CHEF_USERNAME=? FOR UPDATE")) {
                    q.setInt(1, id);
                    q.setString(2, user.getUsername());
                    try (ResultSet r = q.executeQuery()) {
                        if (!r.next()) throw new SQLException("Plan not found.");
                        if (!"APPROVED".equalsIgnoreCase(r.getString("STATUS"))) throw new SQLException("Only approved plans can be marked cooked.");
                        if ("Y".equalsIgnoreCase(r.getString("COOKED"))) throw new SQLException("This plan is already marked cooked.");
                        recipeId = r.getInt("RECIPE_ID");
                        portions = r.getInt("PORTIONS");
                    }
                }
                try (PreparedStatement stock = c.prepareStatement(
                        "SELECT INGREDIENT_ID,QUANTITY FROM RECIPE_INGREDIENTS WHERE RECIPE_ID=?")) {
                    stock.setInt(1, recipeId);
                    try (ResultSet r = stock.executeQuery()) {
                        while (r.next()) {
                            double required = r.getDouble("QUANTITY") * portions;
                            try (PreparedStatement upd = c.prepareStatement(
                                    "UPDATE INGREDIENTS SET STOCK_QTY=STOCK_QTY-?,UPDATED_ON=SYSDATE WHERE INGREDIENT_ID=? AND STOCK_QTY>=?")) {
                                upd.setDouble(1, required);
                                upd.setInt(2, r.getInt("INGREDIENT_ID"));
                                upd.setDouble(3, required);
                                if (upd.executeUpdate() == 0) throw new SQLException("Insufficient stock for ingredient ID " + r.getInt("INGREDIENT_ID"));
                            }
                        }
                    }
                }
                try (PreparedStatement up = c.prepareStatement("UPDATE DAILY_PLANS SET COOKED='Y',RESOLVED_ON=SYSDATE WHERE PLAN_ID=?")) {
                    up.setInt(1, id);
                    up.executeUpdate();
                }
                c.commit();
                logAction("COOKED", "Plan "+id);
                notifyFavouriters(id);
                alertLowStock();
                msg("Plan marked cooked and ingredient stock deducted.");
            } catch (Exception ex) {
                c.rollback();
                msg("Could not complete cooking: " + ex.getMessage());
            }
        } catch (Exception ex) {
            msg("Database error: " + ex.getMessage());
        }
    }
    // Chef: Ingredient Requests tab.
    private JPanel chefRequests() {
        JPanel p=tablePanel("SELECT REQUEST_ID,INGREDIENT_ID,QTY_NEEDED,NOTE,STATUS,MANAGER_NOTE,CREATED_ON FROM INGREDIENT_REQUESTS WHERE CHEF_USERNAME=? ORDER BY CREATED_ON DESC", new Object[]{user.getUsername()});
        JPanel b=buttonBar(p);
        JButton request=new JButton("Request Ingredient");
        AppStyle.styleButton(request);
        b.add(request);
        request.addActionListener(e->requestIngredient());
        return p;
    }
    // Chef asks the manager for more of an ingredient.
    private void requestIngredient() {
        Object[] pk=pickFromList("Choose the ingredient you need", "SELECT INGREDIENT_ID,NAME||'  ('||UNIT||')  -  in stock: '||STOCK_QTY FROM INGREDIENTS ORDER BY NAME");
        if(pk==null)return;
        JTextField qty=new JTextField(), note=new JTextField();
        JPanel f=new JPanel(new GridLayout(2, 2, 8, 8));
        addField(f, "Quantity needed", qty);
        addField(f, "Reason / note", note);
        if(JOptionPane.showConfirmDialog(this, f, "Request: "+pk[1], JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO INGREDIENT_REQUESTS(REQUEST_ID,CHEF_USERNAME,INGREDIENT_ID,QTY_NEEDED,NOTE,STATUS,CREATED_ON) VALUES(SEQ_REQUESTS.NEXTVAL,?,?,?,?,'PENDING',SYSDATE)")) {
            double q=Double.parseDouble(qty.getText().trim());
            if(q<=0)throw new NumberFormatException();
            p.setString(1, user.getUsername());
            p.setInt(2, (Integer)pk[0]);
            p.setDouble(3, q);
            p.setString(4, note.getText());
            p.executeUpdate();
            notifyManagers("INGREDIENT", "Chef "+user.getName()+" requested "+q+" of "+((String)pk[1]).split("  \\(")[0]+".");
            msg("Ingredient request submitted.");
        } catch(NumberFormatException ex) {
            msg("Enter a valid quantity.");
        } catch(Exception ex) {
            msg("Request failed: "+ex.getMessage());
        }
    }

    private static final String CHEF_SQL="SELECT NAME,USERNAME,ROLE,STATUS,CREATED_ON FROM USERS WHERE ROLE='CHEF' ORDER BY CASE STATUS WHEN 'PENDING' THEN 0 WHEN 'ACTIVE' THEN 1 ELSE 2 END,CREATED_ON";
    // Manager: Chefs tab (approve or reject chefs).
    private JPanel chefApprovalPanel() {
        JPanel p=tablePanel(CHEF_SQL, null);
        JTable t=(JTable)p.getClientProperty("table");
        JPanel b=buttonBar(p);
        JButton approve=new JButton("Approve / Reactivate Chef"), reject=new JButton("Reject / Remove Chef");
        AppStyle.styleButton(approve);
        AppStyle.styleButton(reject);
        b.add(approve);
        b.add(reject);
        approve.addActionListener(e->updateChef(t, "ACTIVE"));
        reject.addActionListener(e->updateChef(t, "REJECTED"));
        return p;
    }
    // Changes a chef's status to ACTIVE or REJECTED.
    private void updateChef(JTable t, String status) {
        int row=t.getSelectedRow();
        if(row<0) {
            msg("Select a chef.");
            return;
        }
        String u=t.getValueAt(row, 1).toString();
        try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("UPDATE USERS SET STATUS=? WHERE USERNAME=? AND ROLE='CHEF'")) {
            p.setString(1, status);
            p.setString(2, u);
            p.executeUpdate();
            notifyUser(u, "ACCOUNT", status.equals("ACTIVE")?"Your chef account has been approved.":"Your chef registration was rejected.");
            msg("Chef status updated.");
            logAction("CHEF_"+status, u);
            refreshTable((JPanel)t.getParent().getParent().getParent(), CHEF_SQL, null);
        } catch(Exception ex) {
            msg("Update failed: "+ex.getMessage());
        }
    }
    private static final String PLAN_SQL="SELECT p.PLAN_ID,p.CHEF_USERNAME,p.RECIPE_ID,r.NAME RECIPE_NAME,p.PLAN_DATE,p.PORTIONS,p.STATUS FROM DAILY_PLANS p JOIN RECIPES r ON r.RECIPE_ID=p.RECIPE_ID WHERE p.STATUS='PENDING' ORDER BY p.PLAN_DATE";
    // Manager: Cooking Plan Approvals tab.
    private JPanel planApprovalPanel() {
        JPanel p=tablePanel(PLAN_SQL, null);
        JTable t=(JTable)p.getClientProperty("table");
        JPanel b=buttonBar(p);
        JButton approve=new JButton("Approve Plan"), reject=new JButton("Reject Plan"), refresh=new JButton("Refresh");
        AppStyle.styleButton(approve);
        AppStyle.styleButton(reject);
        AppStyle.styleButton(refresh);
        b.add(approve);
        b.add(reject);
        b.add(refresh);
        approve.addActionListener(e-> {
            resolvePlan(t, "APPROVED");
            refreshTable(p, PLAN_SQL, null);
        });
        reject.addActionListener(e-> {
            resolvePlan(t, "REJECTED");
            refreshTable(p, PLAN_SQL, null);
        });
        refresh.addActionListener(e->refreshTable(p, PLAN_SQL, null));
        return p;
    }
    // Manager approves or rejects the selected plan.
    private void resolvePlan(JTable t, String status) {
        int row=t.getSelectedRow();
        if(row<0) {
            msg("Select a plan.");
            return;
        }
        int id=Integer.parseInt(t.getValueAt(row, 0).toString());
        String chef=t.getValueAt(row, 1).toString();
        String dish=t.getValueAt(row, 3).toString();
        String note=JOptionPane.showInputDialog(this, "Manager note (optional):");
        try {
            if("APPROVED".equals(status))checkPlanStock(id);
            try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("UPDATE DAILY_PLANS SET STATUS=?,MANAGER_NOTE=?,RESOLVED_ON=SYSDATE WHERE PLAN_ID=? AND STATUS='PENDING'")) {
                p.setString(1, status);
                p.setString(2, note);
                p.setInt(3, id);
                if(p.executeUpdate()==0)throw new SQLException("Plan already processed.");
                notifyUser(chef, "PLAN", "Your cooking plan for "+dish+" was "+status+"."+(note==null||note.isBlank()?"":" Note: "+note));
                logAction("PLAN_"+status, dish);
                msg("Plan "+status.toLowerCase()+".");
            }
        } catch(Exception ex) {
            msg("Could not update plan: "+ex.getMessage());
        }
    }
    // Checks stock before a plan is approved. Throws an error listing the shortage.
    private void checkPlanStock(int planId)throws SQLException {
        try(Connection c0=DBConnection.getConnection();PreparedStatement q0=c0.prepareStatement("SELECT 1 FROM DAILY_PLANS WHERE PLAN_ID=? AND TRUNC(PLAN_DATE)<TRUNC(SYSDATE)")) {
            q0.setInt(1, planId);
            try(ResultSet r0=q0.executeQuery()) {
                if(r0.next())throw new SQLException("This plan is for a past date, so it cannot be approved. Please reject it and ask the chef to plan for today.");
            }
        }
        String sql="SELECT i.NAME,ri.QUANTITY*p.PORTIONS REQUIRED_QTY,i.STOCK_QTY,"
                +"NVL((SELECT SUM(ri2.QUANTITY*p2.PORTIONS) FROM DAILY_PLANS p2 JOIN RECIPE_INGREDIENTS ri2 ON ri2.RECIPE_ID=p2.RECIPE_ID "
                +"WHERE ri2.INGREDIENT_ID=ri.INGREDIENT_ID AND p2.STATUS='APPROVED' AND NVL(p2.COOKED,'N')='N' AND p2.PLAN_ID<>p.PLAN_ID),0) RESERVED_QTY "
                +"FROM DAILY_PLANS p JOIN RECIPE_INGREDIENTS ri ON ri.RECIPE_ID=p.RECIPE_ID JOIN INGREDIENTS i ON i.INGREDIENT_ID=ri.INGREDIENT_ID WHERE p.PLAN_ID=?";
        try(Connection c=DBConnection.getConnection();PreparedStatement q=c.prepareStatement(sql)) {
            q.setInt(1, planId);
            boolean any=false;
            StringBuilder short_=new StringBuilder();
            try(ResultSet r=q.executeQuery()) {
                while(r.next()) {
                    any=true;
                    double free=r.getDouble("STOCK_QTY")-r.getDouble("RESERVED_QTY");
                    if(free<r.getDouble("REQUIRED_QTY"))short_.append("\n - ").append(r.getString("NAME")).append(": needs ").append(r.getDouble("REQUIRED_QTY")).append(", free stock ").append(Math.max(free, 0));
                }
            }
            if(!any)throw new SQLException("This recipe has no ingredients entered, so stock cannot be checked. Ask the chef to add them.");
            if(short_.length()>0)throw new SQLException("Insufficient stock (after other approved plans):"+short_);
        }
    }
    private static final String STOCK_SQL="SELECT INGREDIENT_ID,NAME,UNIT,STOCK_QTY,LOW_LEVEL,COST_PER_UNIT,CASE WHEN STOCK_QTY<=0 THEN 'OUT OF STOCK' WHEN STOCK_QTY<=LOW_LEVEL THEN 'LOW' ELSE 'OK' END STATUS FROM INGREDIENTS ORDER BY NAME";
    // Manager: Stock Management tab (restock, add ingredient, set low level).
    private JPanel stockPanel() {
        JPanel p=tablePanel(STOCK_SQL, null);
        JTable t=(JTable)p.getClientProperty("table");
        JPanel b=buttonBar(p);
        JButton restock=new JButton("Restock Selected"), addNew=new JButton("Add New Ingredient"), low=new JButton("Set Low Level"), refresh=new JButton("Refresh");
        AppStyle.styleButton(restock);
        AppStyle.styleButton(addNew);
        AppStyle.styleButton(low);
        AppStyle.styleButton(refresh);
        b.add(restock);
        b.add(addNew);
        b.add(low);
        b.add(refresh);
        refresh.addActionListener(e->refreshTable(p, STOCK_SQL, null));
        restock.addActionListener(e-> {
            int row=t.getSelectedRow();
            if(row<0) {
                msg("Select an ingredient.");
                return;
            }
            int id=Integer.parseInt(t.getValueAt(row, 0).toString());
            String q=JOptionPane.showInputDialog(this, "Quantity to add:");
            if(q==null)return;
            try(Connection c=DBConnection.getConnection();PreparedStatement s=c.prepareStatement("UPDATE INGREDIENTS SET STOCK_QTY=STOCK_QTY+?,UPDATED_ON=SYSDATE WHERE INGREDIENT_ID=?")) {
                double n=Double.parseDouble(q.trim());
                if(n<=0)throw new NumberFormatException();
                s.setDouble(1, n);
                s.setInt(2, id);
                s.executeUpdate();
                logAction("RESTOCK", "Ingredient "+id+" +"+n);
                refreshTable(p, STOCK_SQL, null);
            } catch(NumberFormatException ex) {
                msg("Enter a valid positive number.");
            } catch(Exception ex) {
                msg("Restock failed: "+ex.getMessage());
            }
        });
        low.addActionListener(e-> {
            int row=t.getSelectedRow();
            if(row<0) {
                msg("Select an ingredient.");
                return;
            }
            int id=Integer.parseInt(t.getValueAt(row, 0).toString());
            String q=JOptionPane.showInputDialog(this, "New low-stock level:");
            if(q==null)return;
            try(Connection c=DBConnection.getConnection();PreparedStatement s=c.prepareStatement("UPDATE INGREDIENTS SET LOW_LEVEL=?,UPDATED_ON=SYSDATE WHERE INGREDIENT_ID=?")) {
                double n=Double.parseDouble(q.trim());
                if(n<0)throw new NumberFormatException();
                s.setDouble(1, n);
                s.setInt(2, id);
                s.executeUpdate();
                refreshTable(p, STOCK_SQL, null);
            } catch(NumberFormatException ex) {
                msg("Enter a valid number.");
            } catch(Exception ex) {
                msg("Update failed: "+ex.getMessage());
            }
        });
        addNew.addActionListener(e-> {
            JTextField name=new JTextField(), unit=new JTextField("kg"), qty=new JTextField("0"), lowl=new JTextField("5"), cost=new JTextField("0");
            JPanel f=new JPanel(new GridLayout(0, 2, 8, 8));
            addField(f, "Name", name);
            addField(f, "Unit (kg, l, pcs)", unit);
            addField(f, "Opening stock", qty);
            addField(f, "Low-stock level", lowl);
            addField(f, "Cost per unit", cost);
            if(JOptionPane.showConfirmDialog(this, f, "Add Ingredient", JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            try(Connection c=DBConnection.getConnection();PreparedStatement s=c.prepareStatement("INSERT INTO INGREDIENTS(INGREDIENT_ID,NAME,UNIT,STOCK_QTY,LOW_LEVEL,COST_PER_UNIT,UPDATED_ON) VALUES((SELECT NVL(MAX(INGREDIENT_ID),0)+1 FROM INGREDIENTS),?,?,?,?,?,SYSDATE)")) {
                if(name.getText().trim().isEmpty())throw new IllegalArgumentException("Name is required.");
                s.setString(1, name.getText().trim());
                s.setString(2, unit.getText().trim());
                s.setDouble(3, Double.parseDouble(qty.getText().trim()));
                s.setDouble(4, Double.parseDouble(lowl.getText().trim()));
                s.setDouble(5, Double.parseDouble(cost.getText().trim()));
                s.executeUpdate();
                logAction("INGREDIENT_ADDED", name.getText().trim());
                refreshTable(p, STOCK_SQL, null);
            } catch(NumberFormatException ex) {
                msg("Enter valid numbers.");
            } catch(Exception ex) {
                msg("Could not add ingredient: "+ex.getMessage());
            }
        });
        return p;
    }
    private static final String REQ_SQL="SELECT q.REQUEST_ID,q.CHEF_USERNAME,q.INGREDIENT_ID,q.QTY_NEEDED,q.NOTE,q.STATUS,q.MANAGER_NOTE,i.NAME INGREDIENT_NAME FROM INGREDIENT_REQUESTS q LEFT JOIN INGREDIENTS i ON i.INGREDIENT_ID=q.INGREDIENT_ID WHERE q.STATUS='PENDING' ORDER BY q.CREATED_ON";
    private static final String RECIPE_LIST_SQL="SELECT RECIPE_ID, NAME, PRICE, SERVINGS FROM RECIPES ORDER BY NAME";
    // Manager: Recipes tab (edit the price of a dish).
    private JPanel recipesPanel() {
        JPanel p=tablePanel(RECIPE_LIST_SQL, null);
        JTable t=(JTable)p.getClientProperty("table");
        JPanel b=buttonBar(p);
        JButton price=new JButton("Edit Price of Selected Dish"), refresh=new JButton("Refresh");
        AppStyle.styleButton(price);
        AppStyle.styleButton(refresh);
        b.add(price);
        b.add(refresh);
        refresh.addActionListener(e->refreshTable(p, RECIPE_LIST_SQL, null));
        price.addActionListener(e-> {
            int row=t.getSelectedRow();
            if(row<0) {
                msg("Select a dish first.");
                return;
            }
            int id=Integer.parseInt(t.getValueAt(row, 0).toString());
            String nm=String.valueOf(t.getValueAt(row, 1));
            String v=JOptionPane.showInputDialog(this, "New price for "+nm+" (\u20B9):", String.valueOf(t.getValueAt(row, 2)));
            if(v==null)return;
            try(Connection c=DBConnection.getConnection();PreparedStatement q=c.prepareStatement("UPDATE RECIPES SET PRICE=? WHERE RECIPE_ID=?")) {
                double d=Double.parseDouble(v.trim());
                if(d<0)throw new NumberFormatException();
                q.setDouble(1, d);
                q.setInt(2, id);
                q.executeUpdate();
                logAction("PRICE_SET", nm+" = "+d);
                refreshTable(p, RECIPE_LIST_SQL, null);
            }
                    catch(NumberFormatException ex) {
                msg("Enter a valid price.");
            } catch(Exception ex) {
                msg("Could not update price: "+ex.getMessage());
            }
        });
        return p;
    }
    // Manager: Ingredient Requests tab.
    private JPanel requestApprovalPanel() {
        JPanel p=tablePanel(REQ_SQL, null);
        JTable t=(JTable)p.getClientProperty("table");
        JPanel b=buttonBar(p);
        JButton approve=new JButton("Approve and Restock"), reject=new JButton("Reject"), refresh=new JButton("Refresh");
        AppStyle.styleButton(approve);
        AppStyle.styleButton(reject);
        AppStyle.styleButton(refresh);
        b.add(approve);
        b.add(reject);
        b.add(refresh);
        approve.addActionListener(e-> {
            resolveRequest(t, true);
            refreshTable(p, REQ_SQL, null);
        });
        reject.addActionListener(e-> {
            resolveRequest(t, false);
            refreshTable(p, REQ_SQL, null);
        });
        refresh.addActionListener(e->refreshTable(p, REQ_SQL, null));
        return p;
    }
    // Manager approves (adds the stock) or rejects an ingredient request.
    private void resolveRequest(JTable table, boolean approve) {
        int row=table.getSelectedRow();
        if(row<0) {
            msg("Select a request.");
            return;
        }
        int requestId=Integer.parseInt(table.getValueAt(row, 0).toString());
        String chef=table.getValueAt(row, 1).toString();
        String item=String.valueOf(table.getValueAt(row, 7));
        int ingredientId=Integer.parseInt(table.getValueAt(row, 2).toString());
        double quantity=Double.parseDouble(table.getValueAt(row, 3).toString());
        String note=JOptionPane.showInputDialog(this, "Manager note (optional):");
        try(Connection c=DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                try(PreparedStatement p=c.prepareStatement("UPDATE INGREDIENT_REQUESTS SET STATUS=?,MANAGER_NOTE=?,RESOLVED_ON=SYSDATE WHERE REQUEST_ID=? AND STATUS='PENDING'")) {
                    p.setString(1, approve?"APPROVED":"REJECTED");
                    p.setString(2, note);
                    p.setInt(3, requestId);
                    if(p.executeUpdate()==0)throw new SQLException("Request already processed.");
                }
                if(approve) try(PreparedStatement p=c.prepareStatement("UPDATE INGREDIENTS SET STOCK_QTY=STOCK_QTY+?,UPDATED_ON=SYSDATE WHERE INGREDIENT_ID=?")) {
                    p.setDouble(1, quantity);
                    p.setInt(2, ingredientId);
                    if(p.executeUpdate()==0)throw new SQLException("Ingredient not found.");
                }
                c.commit();
            } catch(Exception ex) {
                c.rollback();
                throw ex;
            }
            notifyUser(chef, "INGREDIENT", "Your request for "+item+" was "+(approve?"approved and stock updated":"rejected")+".");
            logAction("REQUEST_"+(approve?"APPROVED":"REJECTED"), "Request "+requestId);
            msg("Request processed.");
        } catch(Exception ex) {
            msg("Request update failed: "+ex.getMessage());
        }
    }
    // Manager: Reports tab (4 small reports).
    private JPanel reportsPanel() {
        JPanel p=new JPanel(new GridLayout(2, 2, 10, 10));
        p.setBackground(AppStyle.BG);
        p.add(queryPanel("SELECT r.NAME, NVL(SUM(o.QUANTITY),0) TOTAL_PORTIONS_ORDERED FROM RECIPES r JOIN DAILY_PLANS d ON d.RECIPE_ID=r.RECIPE_ID LEFT JOIN ORDERS o ON o.PLAN_ID=d.PLAN_ID AND o.ORDER_STATUS='PLACED' GROUP BY r.NAME ORDER BY TOTAL_PORTIONS_ORDERED DESC", null));
        p.add(queryPanel("SELECT NAME,STOCK_QTY,LOW_LEVEL FROM INGREDIENTS WHERE STOCK_QTY<=LOW_LEVEL ORDER BY NAME", null));
        p.add(queryPanel("SELECT r.NAME,COUNT(f.USERNAME) FAVOURITED_BY FROM RECIPES r LEFT JOIN FAVOURITES f ON f.RECIPE_ID=r.RECIPE_ID GROUP BY r.NAME ORDER BY FAVOURITED_BY DESC", null));
        p.add(queryPanel("SELECT USERNAME,USER_ROLE,ACTION,DETAILS,CREATED_ON FROM ACTIVITY_LOG ORDER BY CREATED_ON DESC", null));
        return p;
    }

    // Notifications tab, same for every role.
    private void addNotificationsTab() {
        String sql="SELECT NOTIF_ID,FROM_USERNAME,NOTIF_TYPE,MESSAGE,IS_READ,CREATED_ON FROM NOTIFICATIONS WHERE TO_USERNAME=? ORDER BY CREATED_ON DESC";
        Object[] args={user.getUsername()};
        JPanel p=tablePanel(sql, args);
        JPanel b=buttonBar(p);
        JButton refresh=new JButton("Refresh"), read=new JButton("Mark All Read");
        AppStyle.styleButton(refresh);
        AppStyle.styleButton(read);
        b.add(refresh);
        b.add(read);
        refresh.addActionListener(e->refreshTable(p, sql, args));
        read.addActionListener(e-> {
            try(Connection c=DBConnection.getConnection();PreparedStatement q=c.prepareStatement("UPDATE NOTIFICATIONS SET IS_READ='Y' WHERE TO_USERNAME=?")) {
                q.setString(1, user.getUsername());
                q.executeUpdate();
                refreshTable(p, sql, args);
            } catch(Exception ex) {
                msg("Could not update: "+ex.getMessage());
            }
        });
        tabs.addTab("Notifications", p);
    }
    // Sends a notification to every manager.
    private void notifyManagers(String type, String message) {
        try(Connection c=DBConnection.getConnection();PreparedStatement q=c.prepareStatement("SELECT USERNAME FROM USERS WHERE ROLE IN ('ADMIN','HOTEL MANAGER') AND STATUS='ACTIVE'");ResultSet r=q.executeQuery()) {
            while(r.next())notifyUser(r.getString(1), type, message);
        } catch(Exception ex) {
            System.err.println("Manager notification failed: "+ex.getMessage());
        }
    }
    // Tells the managers about low or finished ingredients.
    private void alertLowStock() {
        try(Connection c=DBConnection.getConnection();PreparedStatement q=c.prepareStatement("SELECT NAME,STOCK_QTY,LOW_LEVEL FROM INGREDIENTS WHERE STOCK_QTY<=LOW_LEVEL");ResultSet r=q.executeQuery()) {
            while(r.next())notifyManagers("LOW_STOCK", (r.getDouble("STOCK_QTY")<=0?"OUT OF STOCK: ":"Low stock: ")+r.getString("NAME")+" (left "+r.getDouble("STOCK_QTY")+", limit "+r.getDouble("LOW_LEVEL")+").");
        } catch(Exception ex) {
            System.err.println("Low stock check failed: "+ex.getMessage());
        }
    }
    /** Best-effort audit log; never blocks the user's action. */
    // Writes one line in the activity log (who did what).
    private void logAction(String action, String details) {
        try(Connection c=DBConnection.getConnection();PreparedStatement q=c.prepareStatement("INSERT INTO ACTIVITY_LOG(USERNAME,USER_ROLE,ACTION,DETAILS,CREATED_ON) VALUES(?,?,?,?,SYSDATE)")) {
            q.setString(1, user.getUsername());
            q.setString(2, user.getRole());
            q.setString(3, action);
            q.setString(4, details);
            q.executeUpdate();
        } catch(Exception ex) {
            System.err.println("Activity log not saved: "+ex.getMessage());
        }
    }
    // Tells customers that their favourite dish is available.
    private void notifyFavouriters(int planId) {
        String sql="SELECT DISTINCT f.USERNAME,r.NAME FROM DAILY_PLANS p JOIN FAVOURITES f ON f.RECIPE_ID=p.RECIPE_ID JOIN RECIPES r ON r.RECIPE_ID=p.RECIPE_ID WHERE p.PLAN_ID=?";
        try(Connection c=DBConnection.getConnection();PreparedStatement q=c.prepareStatement(sql)) {
            q.setInt(1, planId);
            try(ResultSet r=q.executeQuery()) {
                while(r.next())notifyUser(r.getString(1), "DISH_AVAILABLE", r.getString(2)+" is cooked and available on today's menu.");
            }
        } catch(Exception ex) {
            System.err.println("Favourite notifications failed: "+ex.getMessage());
        }
    }
    // Saves one notification for one user.
    private void notifyUser(String to, String type, String message) {
        try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO NOTIFICATIONS(NOTIF_ID,TO_USERNAME,FROM_USERNAME,NOTIF_TYPE,MESSAGE,IS_READ,CREATED_ON) VALUES(SEQ_NOTIFICATIONS.NEXTVAL,?,?,?,?, 'N',SYSDATE)")) {
            p.setString(1, to);
            p.setString(2, user.getUsername());
            p.setString(3, type);
            p.setString(4, message);
            p.executeUpdate();
        } catch(Exception ex) {
            System.err.println("Notification not saved: "+ex.getMessage());
        }
    }
    // A table that shows the result of an SQL query, with a Refresh button.
    private JPanel queryPanel(String sql, Object[] args) {
        JPanel p=tablePanel(sql, args);
        JButton refresh=new JButton("Refresh");
        AppStyle.styleButton(refresh);
        buttonBar(p).add(refresh);
        refresh.addActionListener(e->refreshTable(p, sql, args));
        return p;
    }
    // Builds the heading and the table.
    private JPanel tablePanel(String sql, Object[] args) {
        JPanel p=new JPanel(new BorderLayout(8, 8));
        p.setBackground(AppStyle.BG);
        p.add(AppStyle.heading("THE VINTAGE RECIPE BOOK"), BorderLayout.NORTH);
        JTable table=new JTable();
        AppStyle.styleTable(table);
        p.putClientProperty("table", table);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        loadTable(table, sql, args);
        return p;
    }
    // Row of buttons at the bottom of a panel.
    private JPanel buttonBar(JPanel p) {
        JPanel b=new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
        b.setBackground(AppStyle.BG);
        p.add(b, BorderLayout.SOUTH);
        return b;
    }
    // Runs the SQL and fills the table. Dates and column headings are tidied here.
    private void loadTable(JTable table, String sql, Object[] args) {
        DefaultTableModel m=new DefaultTableModel();
        try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement(sql)) {
            if(args!=null)for(int i=0;i<args.length;i++)p.setObject(i+1, args[i]);
            try(ResultSet r=p.executeQuery()) {
                ResultSetMetaData md=r.getMetaData();
                for(int i=1;i<=md.getColumnCount();i++)m.addColumn(heading(md.getColumnLabel(i)));
                while(r.next()) {
                    Object[] row=new Object[md.getColumnCount()];
                    for(int i=1;i<=row.length;i++) {
                        int ty=md.getColumnType(i);
                        row[i-1]=(ty==Types.DATE||ty==Types.TIMESTAMP)?fmtDate(r.getTimestamp(i), md.getColumnLabel(i)):r.getObject(i);
                    }
                    m.addRow(row);
                }
            }
        } catch(Exception ex) {
            m.addColumn("Database message");
            m.addRow(new Object[]{ex.getMessage()});
        }
        table.setModel(m);
        AppStyle.styleTable(table);
        fitColumns(table);
    }
    /** PLAN_DATE shows the date only; other date columns show date and HH:mm. */
    // PLAN_DATE shows the date only. Other dates show date and time.
    private static Object fmtDate(java.sql.Timestamp t, String label) {
        if(t==null)return null;
        java.time.LocalDateTime d=t.toLocalDateTime();
        return label.toUpperCase().endsWith("PLAN_DATE")?d.toLocalDate().toString():d.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
    // Column headings: left = database column name, right = heading shown on screen.
    private static final java.util.Map<String, String> HEADINGS = new java.util.HashMap<>();
    static {
        HEADINGS.put("PLAN_ID", "Plan No");
        HEADINGS.put("CHEF_USERNAME", "Chef");
        HEADINGS.put("RECIPE_ID", "Dish No");
        HEADINGS.put("RECIPE_NAME", "Dish");
        HEADINGS.put("PLAN_DATE", "Date");
        HEADINGS.put("PORTIONS", "Portions");
        HEADINGS.put("MANAGER_NOTE", "Manager Note");
        HEADINGS.put("COOKED", "Cooked?");
        HEADINGS.put("INGREDIENT_ID", "Ingredient No");
        HEADINGS.put("INGREDIENT_NAME", "Ingredient");
        HEADINGS.put("INGREDIENT", "Ingredient");
        HEADINGS.put("UNIT", "Unit");
        HEADINGS.put("STOCK_QTY", "Stock");
        HEADINGS.put("LOW_LEVEL", "Low Level");
        HEADINGS.put("COST_PER_UNIT", "Cost per Unit (\u20B9)");
        HEADINGS.put("REQUEST_ID", "Request No");
        HEADINGS.put("QTY_NEEDED", "Quantity Needed");
        HEADINGS.put("NOTE", "Reason");
        HEADINGS.put("NAME", "Name");
        HEADINGS.put("PRICE", "Price (\u20B9)");
        HEADINGS.put("SERVINGS", "Servings");
        HEADINGS.put("SHORT_DESCRIPTION", "Description");
        HEADINGS.put("ORDER_ID", "Order No");
        HEADINGS.put("QUANTITY", "Quantity");
        HEADINGS.put("ORDER_STATUS", "Status");
        HEADINGS.put("NOTIF_ID", "No");
        HEADINGS.put("FROM_USERNAME", "From");
        HEADINGS.put("NOTIF_TYPE", "Type");
        HEADINGS.put("MESSAGE", "Message");
        HEADINGS.put("IS_READ", "Read?");
        HEADINGS.put("TOTAL_PORTIONS_ORDERED", "Portions Ordered");
        HEADINGS.put("FAVOURITED_BY", "Favourited By");
        HEADINGS.put("USERNAME", "User");
        HEADINGS.put("USER_ROLE", "Role");
        HEADINGS.put("ROLE", "Role");
        HEADINGS.put("ACTION", "Action");
        HEADINGS.put("DETAILS", "Details");
        HEADINGS.put("CREATED_ON", "Date & Time");
        HEADINGS.put("STATUS", "Status");
    }
    // Turns a database column name into the heading shown on screen.
    private static String heading(String dbName) {
        String key = dbName.toUpperCase();
        if (HEADINGS.containsKey(key)) return HEADINGS.get(key);
        String result = "";
        for (String word : key.split("_")) {
            if (word.isEmpty()) continue;
            result += word.substring(0, 1) + word.substring(1).toLowerCase() + " ";
        }
        return result.trim();
    }
    /** Sizes each column to its content (long messages are no longer cut off). */
    // Makes each column wide enough for its content.
    private static void fitColumns(JTable t) {
        FontMetrics fm=t.getFontMetrics(t.getFont());
        t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        for(int c=0;c<t.getColumnCount();c++) {
            int w=fm.stringWidth(String.valueOf(t.getColumnName(c)))+34;
            for(int r=0;r<Math.min(t.getRowCount(), 200);r++) {
                Object v=t.getValueAt(r, c);
                if(v!=null)w=Math.max(w, fm.stringWidth(v.toString())+18);
            }
            t.getColumnModel().getColumn(c).setPreferredWidth(Math.min(w, 700));
        }
    }
    // Reloads a table from the database.
    private void refreshTable(JPanel panel, String sql, Object[] args) {
        JTable t=(JTable)panel.getClientProperty("table");
        if(t!=null)loadTable(t, sql, args);
    }
    // One label + input row for pop-up forms.
    private void addField(JPanel p, String label, JComponent field) {
        p.add(new JLabel(label));
        p.add(field);
    }
    // Shows a pop-up message.
    private void msg(String s) {
        JOptionPane.showMessageDialog(this, s);
    }
}