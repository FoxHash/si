/*
 * Copyright 2009 Sven Strickroth <email@cs-ware.de>
 * 
 * This file is part of the SubmissionInterface.
 * 
 * SubmissionInterface is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 * 
 * SubmissionInterface is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with SubmissionInterface. If not, see <http://www.gnu.org/licenses/>.
 */

package de.tuclausthal.submissioninterface.servlets.view;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.tuclausthal.submissioninterface.persistence.datamodel.Task;
import de.tuclausthal.submissioninterface.template.Template;
import de.tuclausthal.submissioninterface.template.TemplateFactory;

/**
 * View-Servlet for displaying a form for adding a function test
 * @author Sven Strickroth
 */
public class TestManagerAddTestFormView extends HttpServlet {
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		Template template = TemplateFactory.getTemplate(request, response);

		PrintWriter out = response.getWriter();

		Task task = (Task) request.getAttribute("task");

		template.printTemplateHeader("Test erstellen", task);
		out.println("<h2>RegExp. Test</h2>");
		out.println("<form action=\"" + response.encodeURL("?") + "\" method=post>");
		out.println("<input type=hidden name=taskid value=\"" + task.getTaskid() + "\">");
		out.println("<input type=hidden name=action value=saveNewTest>");
		out.println("<input type=hidden name=type value=regexp>");
		out.println("<table class=border>");
		out.println("<tr>");
		out.println("<th>Main-Klasse:</th>");
		out.println("<td><input type=text name=mainclass></td>");
		out.println("</tr>");
		out.println("<tr>");
		out.println("<th>CommandLine Parameter:</th>");
		out.println("<td><input type=text name=parameter></td>");
		out.println("</tr>");
		out.println("<tr>");
		out.println("<th>Reg.Exp.:</th>");
		out.println("<td><input type=text name=regexp></td>");
		out.println("</tr>");
		out.println("<tr>");
		out.println("<th>Timeout:</th>");
		out.println("<td><input type=text name=timeout value=15></td>");
		out.println("</tr>");
		out.println("<tr>");
		out.println("<th>Sichtbar f�r Studenten:</th>");
		out.println("<td><input type=checkbox name=visibletostudents></td>");
		out.println("</tr>");
		out.println("<tr>");
		out.println("<td colspan=2 class=mid><input type=submit value=speichern> <a href=\"");
		out.println(response.encodeURL("ShowTask?taskid=" + task.getTaskid()));
		out.println("\">Abbrechen</a></td>");
		out.println("</tr>");
		out.println("</table>");
		out.println("</form>");
		out.println("<p><h2>JUnit. Test</h2>");
		out.println("<form ENCTYPE=\"multipart/form-data\" action=\"" + response.encodeURL("?taskid=" + task.getTaskid() + "&amp;action=saveNewTest&type=junit") + "\" method=post>");
		out.println("<table class=border>");
		out.println("<tr>");
		out.println("<th>Sichtbar f�r Studenten:</th>");
		out.println("<td><input type=checkbox name=visibletostudents></td>");
		out.println("</tr>");
		out.println("<tr>");
		out.println("<th>Timeout:</th>");
		out.println("<td><input type=text name=timeout value=15></td>");
		out.println("</tr>");
		out.println("<tr>");
		out.println("<th>JUnit-Testcase:</th>");
		out.println("<td><INPUT TYPE=file NAME=testcase> (Main Testclass: AllTests)</td>");
		out.println("</tr>");
		out.println("<tr>");
		out.println("<td colspan=2 class=mid><input type=submit value=speichern> <a href=\"");
		out.println(response.encodeURL("ShowTask?taskid=" + task.getTaskid()));
		out.println("\">Abbrechen</a></td>");
		out.println("</tr>");
		out.println("</table>");
		out.println("</form>");
		template.printTemplateFooter();
	}
}