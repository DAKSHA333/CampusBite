package campusbite;

import campusbite.client.CampusFrame;
import campusbite.server.*;
import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import java.awt.Font;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public final class Main {
    public static void main(String[] args) {
        try { run(args); } catch(Exception e){System.err.println("CampusBite: "+e.getMessage());System.exit(1);}
    }
    private static void run(String[] args) throws Exception {
        if(args.length==0||args[0].equals("--help")){help();return;}
        String mode=args[0]; if(!Set.of("server","student","kitchen").contains(mode))throw new IllegalArgumentException("Choose server, student or kitchen. Use --help for options.");
        Map<String,String> options=new HashMap<>();
        for(int i=1;i<args.length;i++){
            String key=args[i];
            if(Set.of("--demo","--reset-demo").contains(key))options.put(key,"true");
            else if(Set.of("--host","--bind","--port","--db","--stock","--profile","--name","--staff-code").contains(key)&&i+1<args.length)options.put(key,args[++i]);
            else throw new IllegalArgumentException("Unknown or incomplete option: "+key);
        }
        int port=Integer.parseInt(options.getOrDefault("--port","5050"));if(port<1||port>65535)throw new IllegalArgumentException("Port must be 1-65535.");
        String staff=options.getOrDefault("--staff-code",System.getenv().getOrDefault("CAMPUSBITE_STAFF_CODE","campus123"));if(staff.isBlank())throw new IllegalArgumentException("Staff code cannot be blank.");
        if(mode.equals("server")){
            int stock=Integer.parseInt(options.getOrDefault("--stock","20"));if(stock<0||stock>100000)throw new IllegalArgumentException("Stock must be 0-100000.");
            boolean demo=options.containsKey("--demo");
            if(options.containsKey("--reset-demo")&&!demo)throw new IllegalArgumentException("Reset requires --demo and only resets data/campusbite-demo.db.");
            if(demo&&options.containsKey("--db"))throw new IllegalArgumentException("Demo mode uses its separate fixed database; omit --db.");
            Path path=Path.of(demo?"data/campusbite-demo.db":options.getOrDefault("--db","data/campusbite.db"));
            Store store=new Store(path,stock,demo);
            if(options.containsKey("--reset-demo")){try(store){store.resetDemo(stock);}System.out.println("Demo reset. Sandwich stock = 1. Use fresh student profiles after reset.");return;}
            TcpServer server;
            try{server=new TcpServer(options.getOrDefault("--bind","127.0.0.1"),port,store,staff);}catch(Exception e){store.close();throw e;}
            Runtime.getRuntime().addShutdownHook(new Thread(()->{server.close();try{store.close();}catch(Exception ignored){}}));
            System.out.println("CampusBite server | database="+path.toAbsolutePath());server.start();new CountDownLatch(1).await();
        }else{
            FlatLightLaf.setup();UIManager.put("defaultFont",new Font("Segoe UI",Font.PLAIN,14));UIManager.put("Button.arc",12);UIManager.put("Component.arc",10);
            SwingUtilities.invokeAndWait(()->{try{new CampusFrame(mode.equals("kitchen"),options.getOrDefault("--host","localhost"),port,options.getOrDefault("--name",""),options.getOrDefault("--profile","default"),staff).setVisible(true);}catch(Exception e){JOptionPane.showMessageDialog(null,e.getMessage(),"CampusBite",JOptionPane.ERROR_MESSAGE);}});
        }
    }
    private static void help(){System.out.println("""
        CampusBite - Java TCP canteen
        java -jar target/campusbite.jar server [--bind 127.0.0.1] [--port 5050] [--db data/campusbite.db] [--stock 20]
        java -jar target/campusbite.jar server --demo [--reset-demo]
        java -jar target/campusbite.jar student [--host localhost] [--port 5050] [--profile student-a] [--name Alice]
        java -jar target/campusbite.jar kitchen [--host localhost] [--port 5050]
        Staff code: CAMPUSBITE_STAFF_CODE environment variable or --staff-code; classroom default campus123.
        """);}
}