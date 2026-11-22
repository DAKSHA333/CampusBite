package campusbite.client;

import campusbite.protocol.Wire;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Shared Swing shell. Socket work runs on a worker; rendering stays on the EDT. */
public final class CampusFrame extends JFrame {
    private static final Color INK=new Color(25,43,40), GREEN=new Color(15,111,84), MUTED=new Color(95,112,106);
    private final boolean kitchen;
    private final Profile profile;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    private volatile Connection connection;
    private final JTextField host,port,name;
    private final JPasswordField code=new JPasswordField(10);
    private final JButton connect=new JButton("Connect"), refresh=new JButton("Refresh"), submit=new JButton("Place order"), disconnect=new JButton("Disconnect");
    private final JLabel state=new JLabel("Offline"), notice=new JLabel("Connect to your canteen to get started."), total=new JLabel("₹0.00");
    private final JPanel menuPanel=new JPanel(new GridLayout(0,2,14,14));
    private final JPanel orderPanel=vertical(),completedPanel=vertical();
    private final JTextArea cart=new JTextArea(7,20);
    private final Map<Integer,JSpinner> quantities=new LinkedHashMap<>();
    private final java.util.List<JButton> statusButtons=new ArrayList<>();
    private JsonNode menu=Wire.JSON.createArrayNode(),orders=Wire.JSON.createArrayNode();
    private boolean busy,dirty,online,closing;
    private int generation;
    private String endpoint;
    private ObjectNode pending;
    private final javax.swing.Timer ageTimer;

    public CampusFrame(boolean kitchen,String serverHost,int serverPort,String displayName,String profileName,String staffCode) throws Exception {
        super("CampusBite • "+(kitchen?"Kitchen":"Student · "+profileName));
        this.kitchen=kitchen;profile=kitchen?null:new Profile(profileName);
        host=new JTextField(serverHost,12);port=new JTextField(String.valueOf(serverPort),5);name=new JTextField(displayName,12);code.setText(staffCode);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        Rectangle workArea=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(Math.min(1040,workArea.width),Math.min(650,workArea.height)));
        setSize(Math.min(1200,workArea.width),Math.min(800,workArea.height));setLocationRelativeTo(null);
        JPanel root=new JPanel(new BorderLayout(0,18));root.setBorder(new EmptyBorder(22,26,22,26));setContentPane(root);
        JPanel top=vertical(),brand=new JPanel(new BorderLayout());
        brand.add(label("CampusBite",30,INK),BorderLayout.WEST);brand.add(label(kitchen?"KITCHEN / LIVE SERVICE":"GOOD FOOD. LESS WAITING.",12,GREEN),BorderLayout.EAST);top.add(brand);
        top.add(Box.createVerticalStrut(8));top.add(label(kitchen?"Every order, one clear queue.":"Your lunch break starts here.",18,MUTED));top.add(Box.createVerticalStrut(18));
        JPanel bar=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        bar.add(new JLabel("Server"));bar.add(host);bar.add(new JLabel("Port"));bar.add(port);bar.add(new JLabel(kitchen?"Staff code":"Your name"));bar.add(kitchen?code:name);
        bar.add(connect);bar.add(disconnect);bar.add(refresh);bar.add(state);top.add(bar);top.add(Box.createVerticalStrut(12));notice.setForeground(GREEN);top.add(notice);root.add(top,BorderLayout.NORTH);
        for(Component component:top.getComponents())if(component instanceof JComponent c)c.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTabbedPane tabs=new JTabbedPane();
        if(kitchen){tabs.addTab("Active orders",scroll(orderPanel));tabs.addTab("Completed orders",scroll(completedPanel));}
        else{
            JPanel shop=new JPanel(new BorderLayout(18,0));shop.add(scroll(menuPanel));
            JPanel basket=new JPanel(new BorderLayout(0,15));basket.setPreferredSize(new Dimension(260,0));basket.setBorder(new CompoundBorder(new LineBorder(new Color(215,225,220)),new EmptyBorder(18,18,18,18)));
            basket.add(label("Your cart",21,INK),BorderLayout.NORTH);cart.setEditable(false);cart.setLineWrap(true);cart.setWrapStyleWord(true);cart.setOpaque(false);basket.add(cart);
            JPanel checkout=vertical();checkout.add(label("TOTAL",12,MUTED));total.setFont(total.getFont().deriveFont(Font.BOLD,28));checkout.add(total);checkout.add(Box.createVerticalStrut(14));checkout.add(submit);checkout.add(Box.createVerticalStrut(12));checkout.add(label("Prices include all charges.",12,MUTED));basket.add(checkout,BorderLayout.SOUTH);
            shop.add(basket,BorderLayout.EAST);tabs.addTab("Today's menu",shop);tabs.addTab("My orders",scroll(orderPanel));renderMenu();
        }
        root.add(tabs);root.add(label(kitchen?"CampusBite / Staff workspace · Orders update live":"CampusBite / Campus canteen · Track your order in My orders",12,MUTED),BorderLayout.SOUTH);
        connect.addActionListener(e->connect());disconnect.addActionListener(e->{generation++;online=false;if(connection!=null)connection.close();state.setText("Offline");controls();});refresh.addActionListener(e->refresh());submit.addActionListener(e->place());
        ageTimer=new javax.swing.Timer(30_000,e->{if(!busy)renderOrders();});ageTimer.start();
        addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){closing=true;if(connection!=null)connection.close();worker.shutdownNow();ageTimer.stop();if(profile!=null)try{profile.close();}catch(Exception ignored){}dispose();}});
        renderOrders();controls();
    }
    private static JPanel vertical(){JPanel p=new JPanel();p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));return p;}
    private static JLabel label(String text,int size,Color color){JLabel l=new JLabel(text);l.setFont(l.getFont().deriveFont((float)size));l.setForeground(color);return l;}
    private static JScrollPane scroll(JPanel p){JScrollPane s=new JScrollPane(p);s.setBorder(null);s.getVerticalScrollBar().setUnitIncrement(20);return s;}
    public static String money(long paise){return String.format(Locale.ROOT,"₹%,d.%02d",paise/100,paise%100);}
    private static String safe(String text){return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private void controls(){
        connect.setEnabled(!busy);connect.setText(online?"Reconnect":"Connect");disconnect.setEnabled(online&&!busy);refresh.setEnabled(online&&!busy);
        host.setEnabled(!online&&!busy);port.setEnabled(!online&&!busy);name.setEnabled(!online&&!busy);code.setEnabled(!online&&!busy);
        submit.setText(pending==null?"Place order":"Retry pending order");submit.setEnabled(online&&!busy&&(pending!=null||cartQuantity()>0));
        quantities.forEach((id,s)->{JsonNode item=findItem(id);s.setEnabled(online&&!busy&&pending==null&&((Integer)s.getValue()>0||item.path("available").asBoolean()&&item.path("stock").asInt()>0));});
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
    private void connect(){
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
        },s->{online=true;state.setText("● Connected");notice.setForeground(GREEN);notice.setText(s.pending==null?"You're connected. All orders and stock are up to date.":"An earlier order is unresolved. Retry it safely using the same reference.");apply(s);});
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
        Map<Integer,Integer> selected=new HashMap<>();quantities.forEach((id,s)->selected.put(id,(Integer)s.getValue()));quantities.clear();menuPanel.removeAll();
        for(JsonNode item:menu){
            int id=item.path("menuItemId").asInt(),stock=item.path("stock").asInt();JPanel card=vertical();card.setBackground(Color.WHITE);card.setBorder(new CompoundBorder(new LineBorder(new Color(219,228,222)),new EmptyBorder(20,20,20,20)));
            card.add(label(item.path("name").asText(),23,INK));card.add(Box.createVerticalStrut(9));card.add(label(item.path("description").asText(),12,MUTED));card.add(Box.createVerticalStrut(16));card.add(label(money(item.path("pricePaise").asLong()),23,GREEN));card.add(Box.createVerticalStrut(8));
            card.add(label(stock==0||!item.path("available").asBoolean()?"Sold out":stock+" remaining",13,stock<=1?new Color(172,80,30):MUTED));card.add(Box.createVerticalStrut(14));
            JPanel row=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));row.setOpaque(false);row.add(new JLabel("Quantity"));JSpinner quantity=new JSpinner(new SpinnerNumberModel(selected.getOrDefault(id,0).intValue(),0,20,1));quantity.setPreferredSize(new Dimension(90,32));((JSpinner.DefaultEditor)quantity.getEditor()).getTextField().setEditable(false);quantity.addChangeListener(e->updateCart());quantities.put(id,quantity);row.add(quantity);card.add(row);menuPanel.add(card);
            for(Component component:card.getComponents())if(component instanceof JComponent c)c.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        if(menu.isEmpty())menuPanel.add(label("Connect to see today's menu.",20,MUTED));menuPanel.revalidate();menuPanel.repaint();updateCart();
    }
    private void updateCart(){long sum=0;StringBuilder text=new StringBuilder();for(var entry:quantities.entrySet()){int q=(Integer)entry.getValue().getValue();if(q>0){JsonNode item=findItem(entry.getKey());long line=item.path("pricePaise").asLong()*q;sum+=line;text.append(q).append(" × ").append(item.path("name").asText()).append("\n").append(money(line)).append("\n\n");}}cart.setText(text.isEmpty()?"Choose something delicious from the menu.":text.toString());total.setText(money(sum));controls();}
    private void place(){
        if(pending==null){pending=Wire.object().put("type","PLACE_ORDER").put("clientOrderId",UUID.randomUUID().toString());var items=pending.putArray("items");quantities.forEach((id,s)->{int q=(Integer)s.getValue();if(q>0)items.add(Wire.object().put("menuItemId",id).put("quantity",q));});}
        ObjectNode request=pending.deepCopy();
        background(()->{
            profile.pending(endpoint,request);
            try{JsonNode result=connection.request(request);profile.pending(endpoint,null);return result;}
            catch(Connection.RemoteError e){if(!e.code.equals("SERVER_BUSY")){profile.pending(endpoint,null);SwingUtilities.invokeLater(()->{pending=null;controls();});}throw e;}
        },order->{pending=null;quantities.values().forEach(s->s.setValue(0));notice.setForeground(GREEN);notice.setText("Order #"+order.path("orderId").asLong()+" confirmed · "+money(order.path("totalPaise").asLong())+" · Track it in My orders.");dirty=true;});
    }
    private void renderOrders(){
        orderPanel.removeAll();completedPanel.removeAll();statusButtons.clear();int active=0,completed=0;
        for(JsonNode order:orders){
            String status=order.path("status").asText();boolean collected=status.equals("COLLECTED");if(collected)completed++;else active++;
            JPanel card=new JPanel(new BorderLayout(15,10));card.setMaximumSize(new Dimension(Integer.MAX_VALUE,200));card.setBackground(Color.WHITE);Color accent=status.equals("READY")?GREEN:status.equals("PREPARING")?new Color(180,114,25):MUTED;card.setBorder(new CompoundBorder(new MatteBorder(0,5,1,1,accent),new EmptyBorder(16,18,16,18)));
            JPanel details=vertical();details.setOpaque(false);details.add(label("#"+order.path("orderId").asLong()+(kitchen?" · "+order.path("studentName").asText():""),22,INK));
            StringBuilder items=new StringBuilder();for(JsonNode item:order.path("items")){if(!items.isEmpty())items.append(" · ");items.append(item.path("quantity").asInt()).append(" × ").append(item.path("name").asText());}details.add(Box.createVerticalStrut(8));details.add(new JLabel("<html>"+safe(items.toString())+"</html>"));details.add(Box.createVerticalStrut(8));
            long minutes=Math.max(0,Duration.between(Instant.parse(order.path("createdAt").asText()),Instant.now()).toMinutes());details.add(label(money(order.path("totalPaise").asLong())+" · "+minutes+" min ago",13,MUTED));card.add(details);
            JPanel action=vertical();action.setOpaque(false);action.add(label(status.equals("READY")?"Ready for pickup":status,18,accent));
            if(kitchen&&!collected){String next=switch(status){case "PLACED"->"PREPARING";case "PREPARING"->"READY";default->"COLLECTED";};JButton button=new JButton("Mark "+next.toLowerCase(Locale.ROOT));statusButtons.add(button);button.setEnabled(online&&!busy);button.addActionListener(e->background(()->connection.request(Wire.object().put("type","UPDATE_ORDER_STATUS").put("orderId",order.path("orderId").asLong()).put("status",next)),result->{notice.setForeground(GREEN);notice.setText("Order #"+result.path("orderId").asLong()+" is "+result.path("status").asText().toLowerCase(Locale.ROOT)+".");dirty=true;}));action.add(Box.createVerticalStrut(12));action.add(button);}
            card.add(action,BorderLayout.EAST);JPanel destination=kitchen&&collected?completedPanel:orderPanel;destination.add(card);destination.add(Box.createVerticalStrut(12));
        }
        if(orders.isEmpty()||kitchen&&active==0)orderPanel.add(label(kitchen?"All caught up. New orders will appear here.":"Your orders will appear here after checkout.",18,MUTED));if(kitchen&&completed==0)completedPanel.add(label("Collected orders will be kept here.",18,MUTED));orderPanel.revalidate();orderPanel.repaint();completedPanel.revalidate();completedPanel.repaint();
    }
}
