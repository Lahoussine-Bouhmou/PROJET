/*
 * zetaNotice.java
 *
 * Zetascribe 28/08/2006
 */
package zetasocket;

import java.io.*;
import java.util.*;
import java.net.*;
import javax.servlet.*;
import javax.servlet.http.*;
import zetasocket.socketSrv;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Transformer;
import org.w3c.dom.Node;
//import org.apache.soap.*;



public class zetaSockHl7 extends GenericServlet
{
    private int dbg;
    private int syncInProgress;
    private ServletConfig cfg;
    private socketSrv controller;

    /*************************************************/
    public void destroy()
    {
        controller.interrupt();
    }
    public boolean getStopThread()
    {
        return(controller.getStopThread());
    }
    public boolean getCtrlThread()
    {
        return(controller.getCtrlThread());
    }


    public void startControl()
    {
        System.out.println("zetaSockHl7 starts socketSrv");
        controller = new socketSrv(cfg);
    }

    private void endControl()
    {
        int port;
        OutputStream out;
        controller.interrupt();
        String s_port = cfg.getInitParameter("port");
        if (s_port != null) port = Integer.parseInt(s_port);
        else port=20002;

        try
        {
            Socket s = new Socket("localhost", port);
            out = s.getOutputStream();
            out.write('e');out.write('n');out.write('d');out.write(0x0d);
            out.flush();
            s.close();
        } catch (IOException e)
        {
        }
    }

    public void init() throws ServletException
    {
        syncInProgress = 0;
        cfg=this.getServletConfig();

        try {
            dbg = Integer.parseInt(cfg.getInitParameter("debug"));
        } catch (Exception e)
        {
            dbg = 0;
        }
        startControl();
        Thread w=new Thread(new zetasocket.Watcher(this, cfg));
        w.start();
        System.out.println("zetaSockHl7 started");

    }



    /*************************************************/
    public void service(ServletRequest request,
                        ServletResponse response)
            throws IOException, ServletException
    {
        try
        {
            String infile="";
            String d = request.getParameter("zs:fnc");

            if (dbg >= 2) System.out.println("zetaSockHl7 gets func");

            if (!getCtrlThread())
            {
                startControl();
            }
            if (d==null)
            {
                System.out.println("Pb recup parametres?");
                for (Enumeration e = request.getParameterNames(); e.hasMoreElements();)
                {
                    String name = (String)e.nextElement();
                    System.out.println("Param : "+name+ " value: "+request.getParameter(name));
                }
                HttpServletResponse r;
                r=(HttpServletResponse) response;
                r.sendError(HttpServletResponse.SC_NOT_FOUND,
                        "No parameters: ");
                return;
            }
            if (d.compareTo("end")==0)
            {
                endControl();
                PrintWriter pw = response.getWriter();
                pw.println("<reply><status>Restart</status></reply>");

                response.flushBuffer();
                return;
            }
            PrintWriter pw = response.getWriter();
            pw.println("<reply><status>Nothing to do</status></reply>");

            response.flushBuffer();
        }
        catch(Exception e)
        {
            HttpServletResponse r;
            r=(HttpServletResponse) response;
            r.sendError(500,"Exception");
            return;
        }
    }
}
