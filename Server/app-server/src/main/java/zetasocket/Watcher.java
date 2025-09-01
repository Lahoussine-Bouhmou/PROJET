package zetasocket;
import javax.servlet.*;
import java.net.*;
import java.io.InputStream;

public class Watcher implements Runnable
{
    private zetaSockHl7 z;
    private Thread t;
    private String urlact;
    int timer;
    Watcher(zetaSockHl7 mainProcess, ServletConfig cfg)
    {
        System.out.println("zetaSockHl7 Watcher starts");
        z = mainProcess;
        urlact = cfg.getInitParameter("urlact");
        String s_timer = cfg.getInitParameter("timer");
        if (s_timer != null) timer = (Integer.parseInt(s_timer) / 5);
        else timer=1;
    }

    public void run()
    {
        int i=0;
        for (;;)
        {
            try
            {
                i++;
                //System.out.println("Watch");
                Thread.sleep(5000);
                if (!z.getCtrlThread())
                {
                    if (z.getStopThread())
                    {
                        return;
                    }
                    Thread.sleep(5000);
                    System.out.println("Watch restart");
                    if (!z.getCtrlThread())
                    {
                        z.startControl();
                    }
                }
                if (z.getStopThread())
                {
                    return;
                }
                if (i >= timer)
                {
                    if (urlact == null) return;
                    getUrl(urlact);
                    i=0;
                }
            }
            catch (Exception e)
            {
                System.out.println("Watch restart exit");
                return;
            }
        }
    }

    private  int getUrl(String urlstr)
    {
        int i;
        String stat="";
        int off=0;
        byte[] b=new byte[8192];

        try
        {
            URL url = new URL(urlstr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            //URLConnection conn = url.openConnection();
            stat="Http";

            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestMethod("GET");



            stat="Sent ";
            Integer j=new Integer(conn.getResponseCode());
            stat += j.toString();
            stat += " " + conn.getResponseMessage();
            String contentType = conn.getContentType();
//System.out.println(stat+" "+contentType);
            InputStream si = conn.getInputStream();
            // Get the response
            stat="Response";

            while((i=si.read())!= -1)
            {
            }
            return 0;
        }
        catch (Exception e)
        {
            System.out.println("getUrl : Caught Exception in call " +urlstr+" step "+stat+" "+
                    e.getMessage());
            return 1;
        }
    }


}