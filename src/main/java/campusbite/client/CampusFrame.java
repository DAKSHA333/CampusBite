package campusbite.client;

import campusbite.protocol.Wire;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static campusbite.client.Design.*;

/** Desktop workspace. Network operations stay on the worker; rendering stays on the EDT. */
public final class CampusFrame extends JFrame {
    private final boolean kitchen;
    private final Profile profile;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    private volatile Connection connection;
    private final JTextField host,port,name;
    private final JPasswordField code=new JPasswordField();
    private final JTextField search=new JTextField();
    private final JButton connect=button("Connect to canteen",true),refresh=button("Refresh",false),disconnect=button("Disconnect",false),submit=button("Place order  →",true);
    private final JLabel state=text("Offline",12,true,MUTED),notice=text("Connect to your canteen to start ordering.",13,false,GREEN),total=text("₹0.00",29,true,INK);
    private final JLabel cartCount=text("0 items",12,false,MUTED),pageTitle=text("Today's menu",25,true,INK),pageSubtitle=text("Fresh picks for your next break.",12,false,MUTED);
    private final JLabel activeCount=text("0",26,true,INK),readyCount=text("0",26,true,GREEN),completedCount=text("0",26,true,INK);
    private final JPanel menuPanel=new JPanel(new GridLayout(0,2,14,14)),orderPanel=column(),completedPanel=column(),cartLines=column();
    private final JPanel pages=new JPanel(new CardLayout());
    private final Map<Integer,JSpinner> quantities=new LinkedHashMap<>();
    private final Map<Integer,JButton> plusButtons=new HashMap<>(),minusButtons=new HashMap<>();
    private final java.util.List<JButton> statusButtons=new ArrayList<>();
    private final Map<String,JButton> navigation=new LinkedHashMap<>(),categories=new LinkedHashMap<>();
    private JsonNode menu=Wire.JSON.createArrayNode(),orders=Wire.JSON.createArrayNode();
    private boolean busy,dirty,online,closing;
    private int generation;
    private String endpoint,category="All",currentPage="menu";
    private ObjectNode pending;
    private final javax.swing.Timer ageTimer;

    public CampusFrame(boolean kitchen,String serverHost,int serverPort,String displayName,String profileName,String staffCode) throws Exception {
        super("CampusBite • "+(kitchen?"Kitchen":"Student · "+profileName));
        this.kitchen=kitchen;profile=kitchen?null:new Profile(profileName);
        host=new JTextField(serverHost);port=new JTextField(String.valueOf(serverPort));name=new JTextField(displayName);code.setText(staffCode);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        Rectangle workArea=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(Math.min(1050,workArea.width),Math.min(680,workArea.height)));
        setSize(Math.min(1400,workArea.width),Math.min(870,workArea.height));setLocationRelativeTo(null);
        JPanel root=new JPanel(new BorderLayout());root.setBackground(BG);setContentPane(root);
        root.add(sidebar(profileName),BorderLayout.WEST);
        JPanel workspace=new JPanel(new BorderLayout(0,16));workspace.setBackground(BG);workspace.setBorder(new EmptyBorder(23,25,20,25));
        workspace.add(header(),BorderLayout.NORTH);pages.setOpaque(false);
        if(kitchen){pages.add(orderWorkspace(false),"orders");pages.add(orderWorkspace(true),"completed");}
        else{pages.add(shop(),"menu");pages.add(orderWorkspace(false),"orders");}
        workspace.add(pages);root.add(workspace);
        connect.addActionListener(e->connectionDialog());refresh.addActionListener(e->refresh());submit.addActionListener(e->place());
        disconnect.addActionListener(e->{generation++;online=false;if(connection!=null)connection.close();state.setText("Offline");notice.setText("You're disconnected. Your saved orders will return when you reconnect.");controls();});
        search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){renderMenu();}public void removeUpdate(DocumentEvent e){renderMenu();}public void changedUpdate(DocumentEvent e){renderMenu();}});
        ageTimer=new javax.swing.Timer(30_000,e->{if(!busy)renderOrders();});ageTimer.start();
        addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){closing=true;if(connection!=null)connection.close();worker.shutdownNow();ageTimer.stop();if(profile!=null)try{profile.close();}catch(Exception ignored){}dispose();}});
        renderMenu();renderOrders();showPage(kitchen?"orders":"menu");controls();
    }

    private JPanel sidebar(String profileName){
        JPanel side=new JPanel(new BorderLayout());side.setBackground(DARK);side.setPreferredSize(new Dimension(190,0));side.setBorder(new EmptyBorder(28,20,24,20));
        JPanel top=column();top.add(text("cb /",36,true,LIME));top.add(Box.createVerticalStrut(5));top.add(text("CampusBite",21,true,Color.WHITE));top.add(Box.createVerticalStrut(6));top.add(text(kitchen?"KITCHEN WORKSPACE":"YOUR CAMPUS CANTEEN",9,true,new Color(160,185,170)));top.add(Box.createVerticalStrut(44));
        top.add(text("WORKSPACE",10,true,new Color(135,165,148)));top.add(Box.createVerticalStrut(14));
        if(!kitchen)addNav(top,"menu","01   Today's menu");addNav(top,"orders",kitchen?"01   Live orders":"02   My orders");if(kitchen)addNav(top,"completed","02   Completed");
        side.add(top,BorderLayout.NORTH);
        JPanel bottom=column();Card note=new Card(new Color(38,70,55),18);note.setLayout(new BorderLayout());note.setBorder(new EmptyBorder(16,12,16,12));note.add(new JLabel("<html><font color='#DDEFAB'><b>"+(kitchen?"Made with care.":"Less queue.<br>More lunch.")+"</b></font><br><br><font color='#ADC3B5'>"+(kitchen?"One queue. Every order.<br>Keep good food moving.":"Order here.<br>Pick up at the counter.")+"</font></html>"));bottom.add(note);bottom.add(Box.createVerticalStrut(20));bottom.add(text(kitchen?"STAFF SESSION":"STUDENT PROFILE",9,true,new Color(135,165,148)));bottom.add(Box.createVerticalStrut(6));bottom.add(text(kitchen?"Campus kitchen":profileName,12,false,Color.WHITE));side.add(bottom,BorderLayout.SOUTH);return side;
    }
    private void addNav(JPanel parent,String key,String title){JButton b=button(title,false);b.setHorizontalAlignment(SwingConstants.LEFT);b.setBorder(new EmptyBorder(13,12,13,6));b.setMaximumSize(new Dimension(Integer.MAX_VALUE,45));b.addActionListener(e->showPage(key));navigation.put(key,b);parent.add(b);parent.add(Box.createVerticalStrut(7));}
    private void showPage(String key){currentPage=key;((CardLayout)pages.getLayout()).show(pages,key);navigation.forEach((id,b)->{b.setBackground(id.equals(key)?LIME:DARK);b.setForeground(id.equals(key)?DARK:new Color(188,207,195));});
        pageTitle.setText(key.equals("menu")?"Today's menu":key.equals("completed")?"Completed orders":kitchen?"Kitchen overview":"My orders");
        pageSubtitle.setText(key.equals("menu")?"Good food. A better lunch break.":key.equals("completed")?"Every pickup, neatly kept in one place.":kitchen?"A little care behind every order.":"From our kitchen to your next break.");}
    private JPanel header(){
        JPanel panel=column(),line=new JPanel(new BorderLayout(12,0));line.setOpaque(false);JPanel titles=column();titles.add(pageTitle);titles.add(Box.createVerticalStrut(4));titles.add(pageSubtitle);line.add(titles);
        JPanel actions=row(9);actions.add(state);actions.add(refresh);actions.add(connect);line.add(actions,BorderLayout.EAST);panel.add(line);panel.add(Box.createVerticalStrut(14));
        Card banner=new Card(new Color(234,240,227),12);banner.setLayout(new BorderLayout());banner.setBorder(new EmptyBorder(10,13,10,13));banner.add(notice);panel.add(banner);return panel;
    }
    private JPanel shop(){
        JPanel shop=new JPanel(new BorderLayout(18,0));shop.setOpaque(false);JPanel left=new JPanel(new BorderLayout(0,15));left.setOpaque(false);
        JPanel introduction=column();Card hero=new Card(DARK,24);hero.setLayout(new BorderLayout());hero.setBorder(new EmptyBorder(20,23,20,12));
        JPanel copy=column();copy.add(text("FRESHLY MADE. CAMPUS PRICED.",10,true,LIME));copy.add(Box.createVerticalStrut(9));copy.add(text("Your break, made better.",27,true,Color.WHITE));copy.add(Box.createVerticalStrut(8));copy.add(text("Pick your favourites. We'll handle the rest.",12,false,new Color(193,210,198)));hero.add(copy);FoodArt art=new FoodArt(1,DARK);art.setPreferredSize(new Dimension(145,110));hero.add(art,BorderLayout.EAST);hero.addComponentListener(new ComponentAdapter(){@Override public void componentResized(ComponentEvent e){boolean visible=hero.getWidth()>620;if(art.isVisible()!=visible){art.setVisible(visible);hero.revalidate();}}});introduction.add(hero);introduction.add(Box.createVerticalStrut(17));
        JPanel filters=new JPanel(new BorderLayout(10,0));filters.setOpaque(false);JPanel chips=row(5);for(String value:new String[]{"All","Bites","Drinks"}){JButton chip=button(value,false);chip.setMargin(new Insets(8,14,8,14));chip.addActionListener(e->{category=value;renderMenu();});categories.put(value,chip);chips.add(chip);}filters.add(chips,BorderLayout.WEST);
        search.putClientProperty("JTextField.placeholderText","Search the menu…");search.putClientProperty("JComponent.roundRect",true);search.setPreferredSize(new Dimension(160,36));filters.add(search);introduction.add(filters);left.add(introduction,BorderLayout.NORTH);
        menuPanel.setOpaque(false);JPanel menuWrap=new JPanel(new BorderLayout());menuWrap.setOpaque(false);menuWrap.add(menuPanel,BorderLayout.NORTH);left.add(scroll(menuWrap));shop.add(left);shop.add(basket(),BorderLayout.EAST);return shop;
    }
    private JPanel basket(){
        Card basket=new Card(Color.WHITE,22);basket.setLayout(new BorderLayout(0,16));basket.setPreferredSize(new Dimension(278,0));basket.setBorder(new EmptyBorder(20,19,20,19));
        JPanel title=new JPanel(new BorderLayout());title.setOpaque(false);title.add(text("Your tray",20,true,INK));title.add(cartCount,BorderLayout.EAST);basket.add(title,BorderLayout.NORTH);basket.add(scroll(cartLines));
        JPanel checkout=column();JSeparator divider=new JSeparator();divider.setForeground(LINE);checkout.add(divider);checkout.add(Box.createVerticalStrut(16));JPanel subtotal=new JPanel(new BorderLayout());subtotal.setOpaque(false);subtotal.add(text("Total",13,false,MUTED),BorderLayout.WEST);subtotal.add(total,BorderLayout.EAST);checkout.add(subtotal);checkout.add(Box.createVerticalStrut(7));checkout.add(text("All charges included",11,false,MUTED));checkout.add(Box.createVerticalStrut(18));submit.setAlignmentX(Component.LEFT_ALIGNMENT);submit.setMaximumSize(new Dimension(Integer.MAX_VALUE,46));checkout.add(submit);checkout.add(Box.createVerticalStrut(12));checkout.add(text("Collect at the canteen counter",10,false,MUTED));basket.add(checkout,BorderLayout.SOUTH);return basket;
    }
    private JPanel orderWorkspace(boolean completed){
        JPanel p=new JPanel(new BorderLayout(0,18));p.setOpaque(false);
        if(kitchen&&!completed){JPanel stats=new JPanel(new GridLayout(1,3,12,0));stats.setOpaque(false);stats.add(stat("IN THE KITCHEN",activeCount));stats.add(stat("READY FOR PICKUP",readyCount));stats.add(stat("COLLECTED",completedCount));p.add(stats,BorderLayout.NORTH);}
        p.add(scroll(completed?completedPanel:orderPanel));return p;
    }
    private JPanel stat(String title,JLabel value){Card card=new Card(Color.WHITE,18);card.setLayout(new BorderLayout(0,10));card.setBorder(new EmptyBorder(16,18,16,18));card.add(text(title,10,true,MUTED),BorderLayout.NORTH);card.add(value);return card;}
    private void connectionDialog(){
        JDialog dialog=new JDialog(this,online?"Connection settings":"Connect to CampusBite",true);JPanel body=new JPanel(new BorderLayout(0,20));body.setBackground(BG);body.setBorder(new EmptyBorder(25,28,25,28));
        JPanel heading=column();heading.add(text("Your canteen, one connection away.",20,true,INK));heading.add(Box.createVerticalStrut(8));heading.add(text("Use localhost on this laptop, or your canteen's server IP.",12,false,MUTED));body.add(heading,BorderLayout.NORTH);
        JPanel fields=new JPanel(new GridLayout(3,2,12,14));fields.setOpaque(false);fields.add(text("Server address",13,true,INK));fields.add(host);fields.add(text("Port",13,true,INK));fields.add(port);fields.add(text(kitchen?"Staff access code":"Your display name",13,true,INK));fields.add(kitchen?code:name);body.add(fields);
        JPanel actions=row(8);JButton cancel=button("Cancel",false),join=button(online?"Reconnect":"Connect",true);cancel.addActionListener(e->dialog.dispose());join.addActionListener(e->{dialog.dispose();connectToServer();});actions.add(cancel);if(online)actions.add(disconnect);actions.add(join);body.add(actions,BorderLayout.SOUTH);dialog.setContentPane(body);dialog.getRootPane().setDefaultButton(join);dialog.pack();dialog.setMinimumSize(new Dimension(510,310));dialog.setLocationRelativeTo(this);dialog.setVisible(true);
    }
    private static JScrollPane scroll(JPanel p){JScrollPane s=new JScrollPane(p);s.setBorder(null);s.setOpaque(false);s.getViewport().setOpaque(false);s.getVerticalScrollBar().setUnitIncrement(22);s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);return s;}
    public static String money(long paise){return String.format(Locale.ROOT,"₹%,d.%02d",paise/100,paise%100);}
    private static String safe(String text){return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private void controls(){
        connect.setEnabled(!busy);connect.setText(busy?"Please wait…":online?"Connection settings":"Connect to canteen");refresh.setEnabled(online&&!busy);disconnect.setEnabled(online&&!busy);
        state.setForeground(online?GREEN:MUTED);host.setEnabled(!online&&!busy);port.setEnabled(!online&&!busy);name.setEnabled(!online&&!busy);code.setEnabled(!online&&!busy);
        submit.setText(pending==null?"Place order  →":"Retry pending order");submit.setEnabled(online&&!busy&&(pending!=null||cartQuantity()>0));
        quantities.forEach((id,s)->{JsonNode item=findItem(id);int q=(Integer)s.getValue();boolean enabled=online&&!busy&&pending==null;s.setEnabled(enabled);if(plusButtons.containsKey(id))plusButtons.get(id).setEnabled(enabled&&q<20&&item.path("available").asBoolean()&&item.path("stock").asInt()>0);if(minusButtons.containsKey(id))minusButtons.get(id).setEnabled(enabled&&q>0);});
        statusButtons.forEach(b->b.setEnabled(online&&!busy));
    }
    private int cartQuantity(){return quantities.values().stream().mapToInt(s->(Integer)s.getValue()).sum();}
    private JsonNode findItem(int id){for(JsonNode item:menu)if(item.path("menuItemId").asInt()==id)return item;return Wire.object();}
    @FunctionalInterface private interface Job<T>{T run() throws Exception;}
    private <T> void background(Job<T> job,Consumer<T> done){
        if(busy||closing)return;busy=true;controls();
        worker.submit(()->{
            T value=null;Exception failure=null;try{value=job.run();}catch(Exception e){failure=e;}
            T result=value;Exception error=failure;
            SwingUtilities.invokeLater(()->{
                if(closing)return;busy=false;
                if(error==null)done.accept(result);else{notice.setForeground(new Color(172,56,38));notice.setText("<html>"+safe(error.getMessage()==null?"Unable to complete the request.":error.getMessage())+"</html>");}
                if(connection==null||!connection.isOpen()){online=false;state.setText("Offline — reconnect");}
                controls();if(dirty&&online){dirty=false;refresh();}
            });
        });
    }
    private void connectToServer(){
        final int p;try{p=Integer.parseInt(port.getText());if(p<1||p>65535)throw new NumberFormatException();}catch(NumberFormatException e){notice.setText("Enter a port between 1 and 65535.");return;}
        String h=host.getText().strip(),n=name.getText().strip(),staff=new String(code.getPassword());if(h.isBlank()){notice.setText("Enter the server address.");return;}
        int epoch=++generation;online=false;if(connection!=null)connection.close();endpoint=h.toLowerCase(Locale.ROOT)+":"+p;state.setText("Connecting…");
        background(()->{
            Connection c=new Connection(h,p,event->SwingUtilities.invokeLater(()->{if(epoch==generation){dirty=true;if(!busy&&online){dirty=false;refresh();}}}),message->SwingUtilities.invokeLater(()->{if(epoch==generation){online=false;state.setText("Offline — reconnect");controls();}}));connection=c;
            try{
                ObjectNode hello=Wire.object().put("type","HELLO").put("role",kitchen?"kitchen":"student");
                if(kitchen)hello.put("staffCode",staff);else hello.put("name",n).put("token",profile.token(endpoint));
                JsonNode identity=c.request(hello);if(!kitchen)profile.token(endpoint,identity.path("token").asText());
                return new Snapshot(c.request(Wire.object().put("type","GET_MENU")),c.allOrders(kitchen),kitchen?null:profile.pending(endpoint));
            }catch(Exception e){c.close();throw e;}
        },s->{online=true;state.setText("Connected");notice.setForeground(GREEN);notice.setText(s.pending==null?"You're connected. All orders and stock are up to date.":"An earlier order is unresolved. Retry it safely using the same reference.");apply(s);});
    }
    private record Snapshot(JsonNode menu,JsonNode orders,ObjectNode pending){}
    private void apply(Snapshot s){
        if(!kitchen)for(JsonNode order:s.orders)if(order.path("status").asText().equals("READY")){
            boolean alreadyReady=false;for(JsonNode old:orders)if(old.path("orderId").equals(order.path("orderId"))&&old.path("status").asText().equals("READY"))alreadyReady=true;
            if(!alreadyReady){notice.setForeground(GREEN);notice.setText("Ready for pickup! Order #"+order.path("orderId").asLong()+" is waiting at the counter.");}
        }
        menu=s.menu;orders=s.orders;pending=s.pending;if(!kitchen)renderMenu();renderOrders();
    }
    private void refresh(){if(!online)return;if(busy){dirty=true;return;}background(()->new Snapshot(connection.request(Wire.object().put("type","GET_MENU")),connection.allOrders(kitchen),pending),this::apply);}

    private void renderMenu(){
        Map<Integer,Integer> selected=new HashMap<>();quantities.forEach((id,s)->selected.put(id,(Integer)s.getValue()));quantities.clear();plusButtons.clear();minusButtons.clear();menuPanel.removeAll();
        categories.forEach((value,b)->{b.setBackground(value.equals(category)?GREEN:Color.WHITE);b.setForeground(value.equals(category)?Color.WHITE:INK);});
        Color[] tints={new Color(244,232,204),new Color(250,229,199),new Color(227,238,211),new Color(249,235,199),new Color(224,239,229),new Color(237,225,211)};
        int shown=0;
        for(JsonNode item:menu){
            int id=item.path("menuItemId").asInt(),stock=item.path("stock").asInt();JSpinner model=new JSpinner(new SpinnerNumberModel(selected.getOrDefault(id,0).intValue(),0,20,1));quantities.put(id,model);
            String itemName=item.path("name").asText();boolean matches=itemName.toLowerCase(Locale.ROOT).contains(search.getText().strip().toLowerCase(Locale.ROOT))&&(category.equals("All")||category.equals("Bites")&&id<=3||category.equals("Drinks")&&id>=4);
            if(!matches){model.addChangeListener(e->updateCart());continue;}shown++;
            Card card=new Card(Color.WHITE,20);card.setLayout(new BorderLayout(0,10));card.setBorder(new EmptyBorder(12,12,14,12));card.add(new FoodArt(id,tints[Math.floorMod(id-1,tints.length)]),BorderLayout.NORTH);
            JPanel detail=column();JPanel title=new JPanel(new BorderLayout(5,0));title.setOpaque(false);title.add(text(itemName,17,true,INK));detail.add(title);detail.add(Box.createVerticalStrut(4));detail.add(text(item.path("description").asText(),12,false,MUTED));detail.add(Box.createVerticalStrut(10));
            JPanel availability=new JPanel(new BorderLayout());availability.setOpaque(false);availability.add(text(money(item.path("pricePaise").asLong()),19,true,INK),BorderLayout.WEST);
            availability.add(pill(stock==0||!item.path("available").asBoolean()?"SOLD OUT":stock==1?"LAST ONE":stock+" left",stock<=1?ORANGE:GREEN,stock<=1?new Color(255,239,219):new Color(235,244,232)),BorderLayout.EAST);detail.add(availability);detail.add(Box.createVerticalStrut(12));
            JPanel footer=new JPanel(new BorderLayout());footer.setOpaque(false);footer.add(text("Add to your tray",10,false,MUTED),BorderLayout.WEST);JPanel stepper=row(3);JButton minus=button("−",false),plus=button("+",true);minus.setMargin(new Insets(4,9,4,9));plus.setMargin(new Insets(4,9,4,9));minus.getAccessibleContext().setAccessibleName("Remove one "+itemName);plus.getAccessibleContext().setAccessibleName("Add one "+itemName);JLabel amount=text(String.valueOf(model.getValue()),13,true,INK);amount.setPreferredSize(new Dimension(24,25));amount.setHorizontalAlignment(SwingConstants.CENTER);minus.addActionListener(e->model.setValue((Integer)model.getValue()-1));plus.addActionListener(e->model.setValue((Integer)model.getValue()+1));model.addChangeListener(e->{amount.setText(String.valueOf(model.getValue()));updateCart();});minusButtons.put(id,minus);plusButtons.put(id,plus);stepper.add(minus);stepper.add(amount);stepper.add(plus);footer.add(stepper,BorderLayout.EAST);detail.add(footer);card.add(detail);menuPanel.add(card);
        }
        if(shown==0)menuPanel.add(empty(menu.isEmpty()?"Something good is on the way.":"No bites found.",menu.isEmpty()?"Connect to see today's freshly made menu.":"Try another search or category."));
        menuPanel.revalidate();menuPanel.repaint();updateCart();
    }
    private void updateCart(){
        cartLines.removeAll();long sum=0;int count=0;
        for(var entry:quantities.entrySet()){
            int quantity=(Integer)entry.getValue().getValue();if(quantity==0)continue;count+=quantity;JsonNode item=findItem(entry.getKey());long line=item.path("pricePaise").asLong()*quantity;sum+=line;
            JPanel linePanel=new JPanel(new BorderLayout(8,0));linePanel.setOpaque(false);linePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE,70));linePanel.setBorder(new EmptyBorder(6,0,12,0));JPanel description=column();description.add(text(item.path("name").asText(),13,true,INK));description.add(Box.createVerticalStrut(5));description.add(text(quantity+" × "+money(item.path("pricePaise").asLong()),11,false,MUTED));linePanel.add(description);linePanel.add(text(money(line),13,true,INK),BorderLayout.EAST);cartLines.add(linePanel);
        }
        if(count==0){JPanel blank=column();blank.add(Box.createVerticalStrut(20));FoodArt trayArt=new FoodArt(3,new Color(246,247,242));trayArt.setMaximumSize(new Dimension(240,130));blank.add(trayArt);blank.add(Box.createVerticalStrut(18));blank.add(text("A little empty in here.",15,true,INK));blank.add(Box.createVerticalStrut(8));blank.add(new JLabel("<html><font color='#6F7B73'>Add your favourites from the menu.<br>Your next good break starts here.</font></html>"));cartLines.add(blank);}
        total.setText(money(sum));cartCount.setText(count+ (count==1?" item":" items"));cartLines.revalidate();cartLines.repaint();controls();
    }
    private JPanel empty(String title,String subtitle){Card p=new Card(Color.WHITE,20);p.setLayout(new BorderLayout(0,12));p.setBorder(new EmptyBorder(32,25,32,25));p.setMaximumSize(new Dimension(Integer.MAX_VALUE,160));p.add(text(title,18,true,INK),BorderLayout.NORTH);p.add(text(subtitle,12,false,MUTED));return p;}
    private void place(){
        if(pending==null){pending=Wire.object().put("type","PLACE_ORDER").put("clientOrderId",UUID.randomUUID().toString());var items=pending.putArray("items");quantities.forEach((id,s)->{int q=(Integer)s.getValue();if(q>0)items.add(Wire.object().put("menuItemId",id).put("quantity",q));});}
        ObjectNode request=pending.deepCopy();
        background(()->{
            profile.pending(endpoint,request);
            try{JsonNode result=connection.request(request);profile.pending(endpoint,null);return result;}
            catch(Connection.RemoteError e){if(!e.code.equals("SERVER_BUSY")){profile.pending(endpoint,null);SwingUtilities.invokeLater(()->{pending=null;controls();});}throw e;}
        },order->{pending=null;quantities.values().forEach(s->s.setValue(0));notice.setForeground(GREEN);notice.setText("Order #"+order.path("orderId").asLong()+" confirmed · "+money(order.path("totalPaise").asLong())+" · Track it in My orders.");showPage("orders");dirty=true;});
    }

    private void renderOrders(){
        orderPanel.removeAll();completedPanel.removeAll();statusButtons.clear();int active=0,ready=0,completed=0;
        for(JsonNode order:orders){
            String status=order.path("status").asText();boolean collected=status.equals("COLLECTED");if(collected)completed++;else active++;if(status.equals("READY"))ready++;
            Color accent=status.equals("READY")?GREEN:status.equals("PREPARING")?ORANGE:MUTED;
            Card card=new Card(Color.WHITE,22);card.setLayout(new BorderLayout(0,15));card.setBorder(new EmptyBorder(20,22,14,22));card.setAlignmentX(Component.LEFT_ALIGNMENT);card.setMaximumSize(new Dimension(Integer.MAX_VALUE,230));
            JPanel heading=new JPanel(new BorderLayout(12,0));heading.setOpaque(false);JPanel identity=column();identity.add(text("Order #"+String.format("%03d",order.path("orderId").asLong())+(kitchen?"  /  "+order.path("studentName").asText():""),20,true,INK));identity.add(Box.createVerticalStrut(5));long minutes=Math.max(0,Duration.between(Instant.parse(order.path("createdAt").asText()),Instant.now()).toMinutes());identity.add(text(minutes==0?"Just placed · Counter pickup":minutes+" min ago · Counter pickup",11,false,MUTED));heading.add(identity);
            JPanel action=row(10);action.add(pill(status.equals("READY")?"READY FOR PICKUP":status,accent,status.equals("PREPARING")?new Color(255,239,219):new Color(235,244,232)));
            if(kitchen&&!collected){String next=switch(status){case "PLACED"->"PREPARING";case "PREPARING"->"READY";default->"COLLECTED";};String caption=switch(next){case "PREPARING"->"Start preparing →";case "READY"->"Mark ready →";default->"Mark collected →";};JButton button=button(caption,true);statusButtons.add(button);button.setEnabled(online&&!busy);button.addActionListener(e->background(()->connection.request(Wire.object().put("type","UPDATE_ORDER_STATUS").put("orderId",order.path("orderId").asLong()).put("status",next)),result->{notice.setForeground(GREEN);notice.setText("Order #"+result.path("orderId").asLong()+" is "+result.path("status").asText().toLowerCase(Locale.ROOT)+".");dirty=true;}));action.add(button);}heading.add(action,BorderLayout.EAST);card.add(heading,BorderLayout.NORTH);
            StringBuilder items=new StringBuilder();for(JsonNode item:order.path("items")){if(!items.isEmpty())items.append(" &nbsp; · &nbsp; ");items.append(item.path("quantity").asInt()).append(" × ").append(safe(item.path("name").asText()));}
            JPanel middle=new JPanel(new BorderLayout(10,0));middle.setOpaque(false);middle.add(new JLabel("<html><font color='#52645A'>"+items+"</font></html>"));middle.add(text(money(order.path("totalPaise").asLong()),21,true,INK),BorderLayout.EAST);card.add(middle);card.add(new Progress(status),BorderLayout.SOUTH);
            JPanel destination=kitchen&&collected?completedPanel:orderPanel;destination.add(card);destination.add(Box.createVerticalStrut(13));
        }
        activeCount.setText(String.valueOf(active-ready));readyCount.setText(String.valueOf(ready));completedCount.setText(String.valueOf(completed));
        if(orders.isEmpty()||kitchen&&active==0)orderPanel.add(empty(kitchen?"All caught up.":"Your next good break starts here.",kitchen?"New orders will appear here as students check out.":"Place an order, then follow its journey right here."));
        if(kitchen&&completed==0)completedPanel.add(empty("Good food, happy students.","Collected orders will appear here for your records."));
        orderPanel.revalidate();orderPanel.repaint();completedPanel.revalidate();completedPanel.repaint();
    }
}