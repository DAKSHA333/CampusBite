package campusbite.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.IOException;

/** Small, local design system. Photography and branding are bundled locally for offline use. */
final class Design {
    static final Color BG=new Color(246,247,242), INK=new Color(25,46,40), MUTED=new Color(111,123,115);
    static final Color GREEN=new Color(34,112,83), DARK=new Color(22,53,43), LIME=new Color(221,239,163);
    static final Color LINE=new Color(230,234,225), ORANGE=new Color(209,102,48);
    static JLabel text(String text,int size,boolean bold,Color color){
        JLabel l=new JLabel(text);l.setFont(new Font("Segoe UI",bold?Font.BOLD:Font.PLAIN,size));l.setForeground(color);return l;
    }
    static JPanel row(int gap){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,gap,0));p.setOpaque(false);return p;}
    static JPanel column(){JPanel p=new JPanel(){
        @Override protected void addImpl(Component component,Object constraints,int index){
            if(component instanceof JComponent child)child.setAlignmentX(Component.LEFT_ALIGNMENT);
            super.addImpl(component,constraints,index);
        }
    };p.setOpaque(false);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));return p;}
    static JButton button(String text,boolean primary){
        JButton b=new JButton(text);b.setFont(new Font("Segoe UI",Font.BOLD,13));b.setFocusPainted(true);
        b.setBackground(primary?GREEN:Color.WHITE);b.setForeground(primary?Color.WHITE:INK);
        b.putClientProperty("JButton.buttonType","roundRect");b.putClientProperty("JComponent.roundRect",true);
        b.setMargin(new Insets(11,17,11,17));b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));return b;
    }
    static JLabel pill(String text,Color foreground,Color background){
        JLabel l=Design.text(text,11,true,foreground);l.setOpaque(true);l.setBackground(background);l.setBorder(new EmptyBorder(5,9,5,9));return l;
    }
    static class Card extends JPanel {
        final Color fill;final int radius;
        Card(Color fill,int radius){this.fill=fill;this.radius=radius;setOpaque(false);}
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);g.fillRoundRect(0,0,getWidth(),getHeight(),radius,radius);g.dispose();super.paintComponent(graphics);
        }
    }
    private static BufferedImage loadImage(String path){
        try(var stream=Design.class.getResourceAsStream("/images/"+path)){
            if(stream==null)throw new IllegalStateException("Missing bundled image: "+path);
            BufferedImage image=ImageIO.read(stream);
            if(image==null)throw new IllegalStateException("Invalid bundled image: "+path);
            return image;
        }catch(IOException e){throw new IllegalStateException("Cannot read bundled image: "+path,e);}
    }
    private static final String[] FOOD_NAMES={"sandwich","samosa","roll","juice","tea","coffee"};
    private static final BufferedImage[] PHOTOS=java.util.Arrays.stream(FOOD_NAMES)
            .map(name->loadImage("food/"+name+".jpg")).toArray(BufferedImage[]::new);
    static final BufferedImage LOGO=loadImage("brand/campusbite-logo.png");
    static JLabel logo(int size){
        JLabel label=new JLabel(new ImageIcon(LOGO.getScaledInstance(size,size,Image.SCALE_SMOOTH)));
        label.getAccessibleContext().setAccessibleName("CampusBite logo");return label;
    }
    static class FoodPhoto extends JComponent {
        final int item;final Color backdrop;
        FoodPhoto(int item,Color backdrop){this.item=item;this.backdrop=backdrop;
            setPreferredSize(new Dimension(190,132));setMinimumSize(new Dimension(40,40));
            setToolTipText(item>=1&&item<=PHOTOS.length?"Serving inspiration: "+FOOD_NAMES[item-1]:"CampusBite");
        }
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setClip(new RoundRectangle2D.Double(0,0,getWidth(),getHeight(),18,18));
            g.setColor(backdrop);g.fillRect(0,0,getWidth(),getHeight());
            BufferedImage photo=item>=1&&item<=PHOTOS.length?PHOTOS[item-1]:LOGO;
            double scale=Math.max(getWidth()/(double)photo.getWidth(),getHeight()/(double)photo.getHeight());
            int width=(int)Math.ceil(photo.getWidth()*scale),height=(int)Math.ceil(photo.getHeight()*scale);
            double focusX=item==6?.74:.5;
            double focusY=switch(item){case 2->.76;case 3->.68;case 4->.72;case 5->.60;default->.5;};
            int x=(int)Math.max(getWidth()-width,Math.min(0,getWidth()/2.0-width*focusX));
            int y=(int)Math.max(getHeight()-height,Math.min(0,getHeight()/2.0-height*focusY));
            g.drawImage(photo,x,y,width,height,null);g.dispose();
        }
    }
    static class Progress extends JComponent {
        final int stage;
        Progress(String status){stage=java.util.List.of("PLACED","PREPARING","READY","COLLECTED").indexOf(status);setPreferredSize(new Dimension(400,55));}
        @Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            String[] labels={"Placed","Preparing","Ready","Collected"};int left=27,right=getWidth()-35,step=(right-left)/3;g.setStroke(new BasicStroke(3));
            for(int i=0;i<3;i++){g.setColor(i<stage?GREEN:LINE);g.drawLine(left+i*step,13,left+(i+1)*step,13);}
            for(int i=0;i<4;i++){int x=left+i*step;g.setColor(i<=stage?GREEN:LINE);g.fillOval(x-6,7,12,12);if(i==stage){g.setColor(LIME);g.fillOval(x-3,10,6,6);}g.setFont(new Font("Segoe UI",i==stage?Font.BOLD:Font.PLAIN,11));g.setColor(i<=stage?GREEN:MUTED);g.drawString(labels[i],x-g.getFontMetrics().stringWidth(labels[i])/2,39);}g.dispose();}
    }
}
