package zetasocket;

import java.io.*;
import java.net.*;
import java.util.Enumeration;
import java.security.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class socketSenderSt extends GenericServlet
{
    private ServletConfig cfg;
    private static Sender senderInstance;
    private String ackMsg;
    private PrintStream flog;
    private int dbg;
    private String host;
    private int port;

    public void startControl()
    {
        int i=0;
        System.out.println("socketSender Sender starts");
        String s_dbg = cfg.getInitParameter("debug");
        if (s_dbg == null) System.out.println("socketSender no debug ");
        if (s_dbg != null) dbg = Integer.parseInt(s_dbg);
        else dbg=3;
        String s_flog = cfg.getInitParameter("logfile");
        if (s_flog != null)
        {
            try
            {
                flog = new PrintStream(new File(s_flog));
            }
            catch(Exception e)
            {
                System.out.println("socketSender issue with logfile : " + s_flog);
                flog = System.out;
            }
        }
        else flog = System.out;
        zetaTrace("socketSender Sender starts");
        String s_port = cfg.getInitParameter("port");
        String host = cfg.getInitParameter("host");
        if (host==null) host="localhost";
        zetaTrace("socketSender port : " + s_port);
        zetaTrace("socketSender host : " + host);
        if (s_port != null) port = Integer.parseInt(s_port);
        else port=20002;
        senderInstance.init(host,port,dbg,flog);
        zetaTrace("socketSender starts on "+s_port);
    }
    public void init() throws ServletException
    {
        cfg=this.getServletConfig();
        startControl();
    }

    public void service(ServletRequest request,
                        ServletResponse response)
            throws IOException, ServletException
    {
        String infile="";
        String d = request.getParameter("zs:fnc");
        String q = request.getParameter("zs:file");
        String hl7 = request.getParameter("hl7msg");
        System.out.println("socketSender Sender service");
        try
        {
            if (d == null)
            {
                d = request.getParameter("zs:act");
            }
            if (d==null)
            {
                zetaTrace("Pb recup parametres?");
                for (Enumeration e = request.getParameterNames(); e.hasMoreElements();)
                {
                    String name = (String)e.nextElement();
                    zetaTrace("Param : "+name+ " value: "+request.getParameter(name));
                }
                HttpServletResponse r;
                r=(HttpServletResponse) response;
                r.sendError(HttpServletResponse.SC_NOT_FOUND,
                        "No parameters: ");
                return;
            }
            // ==== Création de fichier temporaire si on reçoit hl7msg au lieu de zs:file ====
            File temp = null;                                                       // <<< AJOUT
            if ((q == null || q.isEmpty()) && hl7 != null && !hl7.isEmpty()) {      // <<< AJOUT
                temp = File.createTempFile("hl7msg-", ".hl7");                      // <<< AJOUT
                try (FileWriter fw = new FileWriter(temp, false)) {                 // <<< AJOUT
                    fw.write(hl7);                                                  // <<< AJOUT
                }                                                                   // <<< AJOUT
                q = temp.getAbsolutePath();                                         // <<< AJOUT
            }                                                                       // <<< AJOUT
            if (q == null)
            {
                zetaTrace("Pb recup fichier?");
                for (Enumeration e = request.getParameterNames(); e.hasMoreElements();)
                {
                    String name = (String)e.nextElement();
                    zetaTrace("Param : "+name+ " value: "+request.getParameter(name));
                }
                HttpServletResponse r;
                r=(HttpServletResponse) response;
                r.sendError(HttpServletResponse.SC_NOT_FOUND,
                        "No file: ");
                return;
            }
            File f = new File(q);
            f = f.getCanonicalFile();
            if (!f.isFile())
            {
                zetaTrace("Pb fichier incorrect?" + q + " " +  f.getCanonicalPath());
                for (Enumeration e = request.getParameterNames(); e.hasMoreElements();)
                {
                    String name = (String)e.nextElement();
                    zetaTrace("Param : "+name+ " value: "+request.getParameter(name));
                }
                HttpServletResponse r;
                r=(HttpServletResponse) response;
                r.sendError(HttpServletResponse.SC_NOT_FOUND,
                        "Incorrect file: " + q);
                return;
            }
            ackMsg="";
            ackMsg = senderInstance.send(q);
            // suppression du fichier temporaire si utilisé
            if (temp != null) temp.delete();                                            // <<< AJOUT

            response.setContentType("application/xml; charset=utf-8");

            PrintWriter pw = response.getWriter();
            pw.println("<reply><status>"+ackMsg+"</status><infile>"+q+"</infile></reply>");

            response.flushBuffer();
        }
        catch(Exception e)
        {
            response.setContentType("application/xml; charset=utf-8");

            PrintWriter pw = response.getWriter();
            pw.println("<reply><error>" + ackMsg + " " + e.getMessage()+"</error><infile>"+q+"</infile></reply>");

            response.flushBuffer();
        }
        finally
        {
            zetaTrace("socketSender end service");
        }
    }
    private void zetaTrace(String msg)
    {
        zetaTrace(msg, 1);
    }
    private void zetaTrace(String msg, int level)
    {
        if (dbg >= level)
        {
            SimpleDateFormat df= new SimpleDateFormat("yyyy-MM-dd H:m:s.S");
            Date d = new Date();
            flog.println(df.format(d)+" - "+msg);
            flog.flush();
        }
    }

    private static class Sender
    {
        private static int port;
        private static String host;
        private static int dbg;
        private static PrintStream flog;
        private static String ackMsg;

        private static void init(String phost, int pport, int pdbg, PrintStream pflog)
        {
            host= phost;
            port=pport;
            dbg = pdbg;
            flog = pflog;
        }
        private static void zetaTrace(String msg)
        {
            zetaTrace(msg, 1);
        }
        private static void zetaTrace(String msg, int level)
        {
            if (dbg >= level)
            {
                SimpleDateFormat df= new SimpleDateFormat("yyyy-MM-dd H:m:s.S");
                Date d = new Date();
                flog.println(df.format(d)+" - "+msg);
                flog.flush();
            }
        }
        private static void endMessage(DataOutputStream out)
        {
            ackMsg = "endMessage";
            zetaTrace(ackMsg,3);
            try {
                out.write(0x1c);
                out.write(13);
            }
            catch(Exception e){}
        }
        private static void getAck(DataInputStream in) throws IOException, SocketException
        {
            String line;
            String prevline="";
            String input="";
            byte b;
            byte bstr[];
            byte bbuf[];
            int i = 0;
            boolean debMsg, endMsg;
            bbuf = new byte[2048];
            debMsg = false;
            endMsg = false;
            zetaTrace("Attente Ack",2);
            b = 0;
            try {
                b = in.readByte();
            }
            catch(Exception e)
            {
                zetaTrace(e.getMessage());
                return;
            }
            zetaTrace(Byte.toString(b),2);
            zetaTrace("Attente Ack",3);
            while (!debMsg)
            {
                if(b == 11) debMsg = true;
                else if ((i==0) && (b=='M')) i++;
                else if ((i==1) && (b=='S')) i++;
                else if ((i==1) && (b!='S')) i=0;
                else if ((i==2) && (b=='H'))
                {
                    debMsg = true;
                    input = "MSH";
                }
            }
            zetaTrace("Ack entrant",2);
            do
            {
                zetaTrace("Lecture ligne en byte",3);
                i=0;
                do
                {
                    b = in.readByte();
                    if ((b!=10)||(i>0))
                        bbuf[i++] = b;
                    if (b<32) zetaTrace("char"+Byte.toString(b));
                } while ((b != 13)&& !((i>1)&&(b== 10)) && (i < 2048));
                i--;
                bbuf[i]=0;

                line = new String(bbuf,0, i,"ISO-8859-1");
                if (line == null)
                {
                    zetaTrace("Ligne vide",3);
                    return;
                }
                if (line.length() != 0)
                {
                    debMsg = false;
                    bstr = line.getBytes();

                    zetaTrace(line,3);
                    if (line.startsWith("MSA"))
                    {
                        ackMsg=line.substring(4,6);
                        prevline=line;
                    }
                    else if (bstr[line.length() - 1] == 28)
                    {
                        endMsg = true;
                    }
                }
            } while (!endMsg);
            if (ackMsg.compareTo("AA") == 0)
                ackMsg="";
            else
                ackMsg = prevline.substring(7,prevline.length() - 2);
        }
        public static String send(String infile) throws IOException, SocketException
        {
            String line;
            boolean hasLine = true;
            ackMsg="input";
            zetaTrace(ackMsg,3);
            DataInputStream in = new DataInputStream (new FileInputStream(infile));
            ackMsg="socket";
            zetaTrace(ackMsg,3);
            Socket sock = null;
            try
            {
                sock = new Socket(host, port);
                ackMsg="setSoTimeout";
                zetaTrace(ackMsg,3);
                sock.setSoTimeout(10000);
                ackMsg="out";
                zetaTrace(ackMsg,3);
                DataOutputStream out = new DataOutputStream(sock.getOutputStream());
                ackMsg="in";
                zetaTrace(ackMsg,3);
                DataInputStream ack = new DataInputStream(sock.getInputStream());
                int i=0;
                int b=0;
                ackMsg="write";
                zetaTrace(ackMsg,3);
                out.write(0x0b);
                do
                {
                    zetaTrace("Lecture ligne",3);
                    ackMsg="line";
                    zetaTrace(ackMsg,3);
                    line = in.readLine();
                    if (line != null) zetaTrace(line,3);
                    if (line == null)
                    {
                        endMessage(out);
                        hasLine = false;
                    }
                    else if (line.length() <= 2)
                    {
                        endMessage(out);
                        hasLine = false;
                    }
                    else
                    {
                        out.writeBytes(line);
                        out.write(0x0a);
                    }
                } while(hasLine);
                ackMsg="ack?";
                zetaTrace(ackMsg,3);
                getAck(ack);
            }
            finally
            {
                if (sock != null) sock.close();
            }
            return ackMsg;
        }
    }
}