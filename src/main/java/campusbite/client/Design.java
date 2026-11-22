package campusbite.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.*;

/** Small, local design system. Artwork is drawn in Java: no network or image dependency. */
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
    static class FoodArt extends JComponent {
        final int item;final Color backdrop;
        FoodArt(int item,Color backdrop){this.item=item;this.backdrop=backdrop;setPreferredSize(new Dimension(190,112));setMinimumSize(new Dimension(80,75));setToolTipText("Illustration of "+switch(item){case 1->"sandwich";case 2->"samosa";case 3->"vegetable roll";case 4->"juice";case 5->"tea";default->"coffee";});}
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(backdrop);g.fillRoundRect(0,0,getWidth(),getHeight(),22,22);
            double scale=Math.min(getWidth()/220.0,getHeight()/120.0);g.translate(getWidth()/2.0-110*scale,getHeight()/2.0-60*scale);g.scale(scale,scale);
            g.setColor(new Color(255,255,255,90));g.fillOval(51,5,117,109);
            g.setColor(new Color(38,49,33,22));g.fillOval(51,96,120,12);
            switch(item){case 1->sandwich(g);case 2->samosa(g);case 3->roll(g);case 4->juice(g);default->cup(g,item==6);}
            g.dispose();
        }
        private void sandwich(Graphics2D g){
            polygon(g,new Color(193,128,56),56,80,119,34,169,82,108,107);
            polygon(g,new Color(245,199,115),54,74,117,29,169,75,108,101);
            polygon(g,new Color(64,134,61),53,67,115,24,171,69,107,94);
            g.setColor(new Color(197,60,45));g.fillOval(76,60,73,22);
            polygon(g,new Color(255,222,132),53,61,116,19,172,62,109,89);
            polygon(g,new Color(194,130,60),54,52,114,12,172,53,111,80);
            polygon(g,new Color(251,225,172),58,48,114,14,166,50,111,73);
            g.setColor(new Color(216,175,108));for(int i=0;i<6;i++)g.fillOval(88+i*9,38+(i%2)*11,4,3);
        }
        private void samosa(Graphics2D g){
            polygon(g,new Color(179,104,31),51,93,91,23,132,95);polygon(g,new Color(243,178,70),55,88,91,24,123,88);
            polygon(g,new Color(198,125,39),97,96,139,30,177,96);polygon(g,new Color(250,195,91),104,91,139,30,170,91);
            g.setColor(new Color(184,116,35));for(int i=0;i<7;i++)g.fillOval(76+i*12,70+(i%3)*6,3,3);
            g.setColor(new Color(248,250,234));g.fillOval(47,87,32,16);g.setColor(new Color(60,126,66));g.fillOval(51,89,24,10);
        }
        private void roll(Graphics2D g){
            g.rotate(-.25,110,60);g.setColor(new Color(190,126,62));g.fillRoundRect(60,36,111,53,24,24);
            g.setColor(new Color(244,211,151));g.fillRoundRect(57,30,112,53,24,24);
            g.setColor(new Color(220,182,117));for(int i=0;i<5;i++)g.fillOval(78+i*15,44+(i%2)*16,7,4);
            g.setColor(new Color(248,247,236));g.fillRoundRect(103,30,47,56,4,4);
            g.setColor(new Color(53,121,64));g.fillOval(51,34,22,44);g.setColor(new Color(221,86,51));g.fillOval(52,46,13,14);g.setColor(new Color(245,192,65));g.fillOval(59,63,11,9);
        }
        private void juice(Graphics2D g){
            g.setStroke(new BasicStroke(5,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.setColor(new Color(41,114,85));g.drawLine(119,50,129,13);g.drawLine(129,13,145,9);
            polygon(g,new Color(240,158,46),80,32,141,32,132,99,89,99);polygon(g,new Color(255,191,62),85,36,136,36,128,94,93,94);
            g.setColor(new Color(255,241,184));g.fillRoundRect(93,46,8,38,6,6);g.setColor(new Color(235,132,39));g.fillOval(137,70,37,37);g.setColor(new Color(255,219,112));g.fillOval(141,74,29,29);
            g.setColor(new Color(251,245,203));for(int i=0;i<6;i++){double a=i*Math.PI/3;g.drawLine(155,89,(int)(155+12*Math.cos(a)),(int)(89+12*Math.sin(a)));}
        }
        private void cup(Graphics2D g,boolean coffee){
            g.setColor(new Color(255,255,248));g.fillOval(63,87,98,17);g.setStroke(new BasicStroke(9));g.setColor(coffee?new Color(119,73,50):new Color(67,132,105));g.drawOval(134,49,28,29);
            g.setColor(coffee?new Color(153,101,69):new Color(80,150,117));g.fillRoundRect(72,44,72,51,17,17);g.setColor(new Color(245,238,214));g.fillOval(72,36,72,23);g.setColor(coffee?new Color(81,48,34):new Color(165,109,61));g.fillOval(78,40,60,15);
            g.setColor(new Color(255,255,255,140));g.setStroke(new BasicStroke(3,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            for(int i=0;i<3;i++){int x=94+i*15;g.draw(new CubicCurve2D.Float(x,29,x-10,19,x+8,17,x,8));}
            g.setColor(new Color(255,255,255,80));g.fillRoundRect(81,57,7,25,5,5);
        }
        private static void polygon(Graphics2D g,Color color,int... xy){int[] x=new int[xy.length/2],y=new int[x.length];for(int i=0;i<x.length;i++){x[i]=xy[i*2];y[i]=xy[i*2+1];}g.setColor(color);g.fillPolygon(x,y,x.length);}
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
