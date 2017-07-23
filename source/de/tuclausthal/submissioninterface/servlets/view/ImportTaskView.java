/*
 * Copyright 2013 Sven Strickroth <email@cs-ware.de>
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

import de.tuclausthal.submissioninterface.persistence.datamodel.Lecture;
import de.tuclausthal.submissioninterface.persistence.datamodel.TaskGroup;
import de.tuclausthal.submissioninterface.template.Template;
import de.tuclausthal.submissioninterface.template.TemplateFactory;
import de.tuclausthal.submissioninterface.util.Util;

/**
 * View-Servlet for displaying a form for importing a task
 * @author Sven Strickroth
 */
public class ImportTaskView extends HttpServlet {
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		Template template = TemplateFactory.getTemplate(request, response);

		Lecture lecture = (Lecture)request.getAttribute("lecture");

		template.printTemplateHeader("Aufgabe importieren", lecture);

		PrintWriter out = response.getWriter();
		out.println("<form class=mid ENCTYPE=\"multipart/form-data\" action=\"" + response.encodeURL("?lecture=" + lecture.getId()) + "\" method=post>");
		out.println("<input type=hidden name=lecture value=\"" + lecture.getId() + "\">");
		out.println("<table class=border>");
		out.println("<tr>");
		out.println("<th width=\"30%\">Aufgabengruppe:</th>");
		out.println("<td><select size=1 name=taskGroup required=required>");
		for (TaskGroup taskGroup : lecture.getTaskGroups()) {
			out.println("<option value=\"" + taskGroup.getTaskGroupId() + "\">" + Util.escapeHTML(taskGroup.getTitle()) + "</option>");
		}
		out.println("</select></td>");
		out.println("</tr>");
		out.println("<tr>");
		out.println("<th>Titel:</th>");
		out.println("<td><input type=text size=100 name=title> (leer um importierten Titel zu nutzen)</td>");
		out.println("</tr>");
		out.println("<tr>");
		out.println("<th width=\"30%\">Zu importierende Datei:</th>");
		out.println("<td><INPUT TYPE=file NAME=file required=required></td>");
		out.println("</tr>");
		out.println("<tr>");
		out.print("<td colspan=2 class=mid><input type=submit value=importieren> <a href=\"");
		out.print(response.encodeURL("ShowLecture?lecture=" + lecture.getId()));
		out.println("\">Abbrechen</a></td>");
		out.println("</tr>");
		out.println("</table>");
		out.println("</form>");
		template.printTemplateFooter();
	}
}
