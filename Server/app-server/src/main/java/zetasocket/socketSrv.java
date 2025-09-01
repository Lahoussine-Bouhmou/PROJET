package zetasocket;

import java.io.*;
import java.net.*;
import java.security.*;
import javax.servlet.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class socketSrv
{
    private ServletConfig cfg;
    private int port,maxConnections;
    private doComms t;
    private int dbg;
    private int timeout;
    private PrintStream flog;
    private String host_restrict;
    // Listen for incoming connections and handle them

    socketSrv(ServletConfig p_cfg)
    {
        int i=0;
        System.out.println("zetaSockHl7 Listener starts");
        cfg = p_cfg;
        maxConnections = 0;
        String s_port = cfg.getInitParameter("port");
        if (s_port != null) port = Integer.parseInt(s_port);
        else port=20002;
        String s_timout = cfg.getInitParameter("timeout");
        if (s_timout != null) timeout = Integer.parseInt(s_timout);
        else timeout=5000;
        String s_dbg = cfg.getInitParameter("debug");
        if (s_dbg != null) dbg = Integer.parseInt(s_dbg);
        else dbg=0;
        String folder = cfg.getInitParameter("folder");
        if (folder == null) return;
        String host_restrict = cfg.getInitParameter("host");
        String urlact = cfg.getInitParameter("urlact");
        String endRecord = cfg.getInitParameter("endrecord");
        String s_flog = cfg.getInitParameter("logfile");
        if (s_flog != null)
        {
            try
            {
                flog = new PrintStream(new File(s_flog));
            }
            catch(Exception e)
            {
                flog = System.out;
            }
        }
        else flog = System.out;

        if (dbg > 0) System.out.println("SocketSrv starts on "+s_port);
        t = new doComms(port, folder, urlact, dbg, flog, endRecord, host_restrict);
        t.start();
        if (dbg > 0) System.out.println("SocketSrv started");
    }

    public boolean getCtrlThread()
    {

        if (dbg > 0) System.out.println("SocketSrv getCtrlThread " + (t.isAlive()?"alive":"dead") + (t.stopThread?" stop":""));
        return(t.isAlive());
    }
    public boolean getStopThread()
    {

        if (dbg > 0) System.out.println("SocketSrv getStopThread " + (t.isAlive()?"alive":"dead") + (t.stopThread?" stop":""));
        return(t.stopThread);
    }

    void  interrupt()
    {
        if (dbg > 0) System.out.println("SocketSrv interrupt");
        maxConnections = -1;
        if (t.isAlive())
            t.stopMe();
    }

    private class doComms extends Thread
    {
        private Socket server;
        private String line,input, endRecord;
        private String folder,urlact, host;
        boolean endMsg;
        boolean debMsg;
        private int port;
        private int dbg;
        private boolean stopThread = false;
        private	DataInputStream in;
        private DataOutputStream out;
        private ServerSocket listener;
        private PrintStream flog;

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

        doComms(int p_port, String p_folder, String p_urlact, int p_dbg, PrintStream p_flog, String p_endRecord)
        {
            host = null;
            port = p_port;
            folder = p_folder;
            urlact = p_urlact;
            dbg = p_dbg;
            flog = p_flog;
            endRecord = p_endRecord;
        }
        doComms(int p_port, String p_folder, String p_urlact, int p_dbg, PrintStream p_flog, String p_endRecord, String host_restrict)
        {
            host = host_restrict;
            port = p_port;
            folder = p_folder;
            urlact = p_urlact;
            dbg = p_dbg;
            flog = p_flog;
            endRecord = p_endRecord;
        }
        public void stopMe()
        {
            try {
                if (in != null)
                {
                    in.close();
                    in = null;
                }
            } catch(Exception e){zetaTrace("Inputstream not closable" + e.getMessage(),0);}
            this.stopWorker();
        }

        public void run ()
        {
            boolean connOk = true;

            while(true)
            {
                if (this.stopThread)
                {
                    try
                    {
                        listener.close();
                    }
                    catch (IOException ioe)
                    {
                        zetaTrace("IOException on socket close: " + ioe);
                        ioe.printStackTrace();
                    }
                    zetaTrace("Synchronized exit ");
                    return;
                }
                zetaTrace("zetaSockHl7 run start");
                newMessage();
                try
                {
                    File f = new File(folder);
                    // Ajout de (!f.exists())
                    if (!f.exists())
                    {
                        if (f.mkdirs()) {
                            zetaTrace("Created folder at " + folder);
                        } else {
                            zetaTrace("Failed to create folder at " + folder);
                            return;
                        }
                    }
                    if (!f.isDirectory())
                    {
                        zetaTrace("No folder at "+folder);
                        return;
                    }
                    if (host != null)
                    {
                        InetAddress ia = InetAddress.getByName(host);
                        listener = new ServerSocket(port,1, ia);
                    }
                    else
                        listener = new ServerSocket(port);
                    listener.setSoTimeout(timeout);
                    listener.setReuseAddress(true);
                    server = listener.accept();
                }
                catch (SocketTimeoutException e)
                {
                    zetaTrace("Timeout in accept ended ");
                }
                catch (IOException ioe)
                {
                    zetaTrace("IOException on socket listen: " + ioe);
                    ioe.printStackTrace();
                    server = null;
                }
                connOk = true;
                try
                {
                    loop();
                    zetaTrace("zetaSockHl7 Exit from loop",3);

                    synchronized(this)
                    {
                        if (this.stopThread)
                        {
                            try
                            {
                                listener.close();
                            }
                            catch (IOException ioe)
                            {
                                zetaTrace("IOException on socket close: " + ioe);
                                ioe.printStackTrace();
                            }
                            zetaTrace("Synchronized exit ");
                            return;
                        }
                    }
                }
                catch (Exception e)
                {
                    connOk=false;
                    zetaTrace("zetaSockHl7 Listener Exception");
                    try
                    {
                        listener.close();
                        Thread.sleep(100);
                    }
                    catch (IOException ioe)
                    {
                        zetaTrace("IOException on socket close: " + ioe);
                        ioe.printStackTrace();
                    }
                    catch (Exception inte)
                    {
                        zetaTrace("Exception after socket close: " + inte);
                    }
                    server = null;
                }
                try
                {
                    listener.close();
                }
                catch (IOException ioe)
                {
                    zetaTrace("IOException on socket listen: " + ioe);
                    ioe.printStackTrace();
                    server = null;
                }
            }
        }

        public synchronized void stopWorker()
        {
            zetaTrace("Synchronized exit asked for");
            this.stopThread = true;
            this.interrupt();
            try {
                if (in != null) in.close();
            } catch(Exception e){zetaTrace("Inputstream not closable" + e.getMessage(),0);}
        }

        void loop () throws InterruptedException
        {
            String line;
            input="";
            byte b;
            byte bstr[];
            byte bbuf[];
            int i;
            bbuf = new byte[2048];
            zetaTrace("Restart loop",3);
            try
            {
                // Get input from the client
                in = new DataInputStream (server.getInputStream());
                out = new DataOutputStream(server.getOutputStream());
            }
            catch (EOFException e)
            {
                zetaTrace("EOFException on socket open: " + e.getMessage(), 3);
                return;

            }
            catch (IOException ioe)
            {
                zetaTrace("IOException on socket open: " + ioe.getMessage(), 3);
                return;
            }
            try
            {
                do
                {
                    debMsg = false;
                    i=0;
                    b=0;
                    zetaTrace("Attente de message",3);
                    endMsg = false;
                    while (!debMsg)
                    {
                        b = in.readByte();
                        zetaTrace(Byte.toString(b),2);
                        if (b==28)
                        {
                            b = in.readByte();
                            endMsg=true;
                            endMessage();
                            newMessage();
                            if (in.available() <= 0) return;
                        }
                        if(b == 11) debMsg = true;
                        else if ((i==0) && (b=='M')) i++;
                        else if ((i==1) && (b=='S')) i++;
                        else if ((i==1) && (b!='S')) i=0;
                        else if ((i==2) && (b=='H'))
                        {
                            debMsg = true;
                            input = "MSH";
                        }
                        else if ((i==2) && (b!='H')) i=0;
                    }
                    zetaTrace("Message entrant",2);
                    while (!endMsg)
                    {
                        zetaTrace("Lecture ligne en byte",3);
                        i=0;
                        do
                        {
                            b = in.readByte();
                            if ((b!=10)||(i>0))
                                bbuf[i++] = b;
                            if (b<32) zetaTrace("char"+Byte.toString(b));
                        } while ((b != 13)&& !((i>1)&&(b== 10)) && (i < 2047));
                        if (i < 2047) i--;
                        bbuf[i]=0;

                        line = new String(bbuf,0, i,"ISO-8859-1");
                        if (line == null)
                        {
                            zetaTrace("Ligne vide",3);
                            return;
                        }
                        if (line.startsWith("end"))
                        {
                            zetaTrace("Ligne end",3);
                            return;
                        }
                        if ((!debMsg) && line.startsWith("MSH"))
                        {
                            endMessage();
                            zetaTrace("Multiple messages");
                            newMessage();
                        }
                        zetaTrace("ligne",3);
                        if (line.length() != 0)
                        {
                            debMsg = false;
                            bstr = line.getBytes();

                            zetaTrace(line,3);
                            if (line.length() <= 3)
                            {
                                if (dbg > 1) zetaTrace("Rec separator",3);
                                for (i=0; i<line.length(); i++)
                                {
                                    zetaTrace(Byte.toString(bstr[i]),3);
                                    if (bstr[i] == 28)
                                    {
                                        zetaTrace("Rec end",3);
                                        endMsg = true;
                                        endMessage();
                                    }
                                    if (bstr[i] == 11)
                                    {
                                        zetaTrace("Rec start without end???",3);
                                        endMessage();
                                        newMessage();
                                    }
                                }
                            }
                            else if (bstr[line.length() - 1] == 28)
                            {
                                input += new String(bstr,0, line.length() - 1,"ISO-8859-1") + "\n";
                                if (dbg > 1) zetaTrace("Rec end at end of segment",3);
                                endMsg = true;
                                endMessage();
                            }
                            // J'ai changé: //((endRecord != null) && (line.startsWith("MRG") || line.startsWith(endRecord))) en :
                            else if ((endRecord != null) && (line.startsWith(endRecord)))
                            {
                                input += line + "\n";
                                if (dbg > 1) zetaTrace("Rec " + endRecord + " - end",3);
                                endMsg = true;
                                endMessage();
                            }
                            else
                            {
                                input += line;
                                if (i < 2047) input += "\n";
                            }
                        }
                    }
                }
                while (true);
            }
            catch (EOFException e)
            {
                zetaTrace("EOFException on socket listen: " + e.getMessage(), 3);
                if (input.length() > 0)
                    endMessage();
                else
                    zetaTrace("Pas d'input en cours", 3);

            }
            catch (IOException ioe)
            {
                zetaTrace("IOException on socket listen: " + ioe.getMessage(), 3);
                ioe.printStackTrace();
            }
        }
        private void ack(String msg, DataOutputStream out)
        {
            String lines[] = msg.split("\\n");
            String segs[] = lines[0].split("\\|");
            if (segs[0].compareTo("MSH") != 0)
            {
                zetaTrace("Pas ack car pas MSH",3);
                return;
            }
            if (segs.length < 10) return;
            zetaTrace("ACK="+segs[0]+" "+segs[9],2);
            String fn = folder+"/"+segs[9]+".hl7";
            String fn2 = folder+"/"+segs[9]+".ok";
            try
            {
                FileOutputStream f = new FileOutputStream(fn);
                f.write(msg.getBytes());
                f.close();
                f = new FileOutputStream(fn2);
                f.close();
            }
            catch (IOException ioe)
            {
                zetaTrace("IOException on write to file: " + fn);
                return;
            }

            zetaTrace("Ack Message entrant",3);
            try
            {
                out.write(0x0b);
                out.writeBytes(lines[0]);
                out.write(13);
//				out.write(10);
                out.writeBytes("MSA|AA|");
                out.writeBytes(segs[9]);
                out.writeBytes("||");
                out.write(13);
//				out.write(10);
                out.write(0x1c);
                out.write(13);
//				out.write(10);
            }
            catch (IOException ioe)
            {
                zetaTrace("IOException on write ACK to socket: " + segs[9]);
                return;
            }
        }

        private void endMessage()
        {
            ack(input, out);
            input="";
        }
        private void newMessage()
        {
            debMsg=true;
            input="";
        }
    }
}