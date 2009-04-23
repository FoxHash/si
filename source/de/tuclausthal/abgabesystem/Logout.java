package de.tuclausthal.abgabesystem;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Logout extends HttpServlet {

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		MainBetterNameHereRequired.getServletRequest().removeAttribute("user");
		request.getSession().invalidate();
		MainBetterNameHereRequired mainbetternamereq = new MainBetterNameHereRequired(request, response);
		MainBetterNameHereRequired.template().printTemplateHeader("Logged out");
		PrintWriter out = response.getWriter();
		out.println("<div class=mid><a href=\"" + response.encodeURL("/ba/servlets/Overview") + "\">zur �bersicht</a></div>");
		MainBetterNameHereRequired.template().printTemplateFooter();
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		// don't want to have any special post-handling
		doGet(request, response);
	}
}
