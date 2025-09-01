package zetasocket;

import javax.servlet.http.*;
import javax.servlet.*;
import java.io.IOException;

public class HealthServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(200);
        resp.setContentType("text/plain");
        resp.getWriter().println("OK");
    }
}
